package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.MedicalRecord;
import com.vetclinic.booking.domain.PrescriptionItem;
import com.vetclinic.booking.domain.PrescriptionStatus;
import com.vetclinic.booking.dto.MedicalRecordRequest;
import com.vetclinic.booking.dto.MedicalRecordResponse;
import com.vetclinic.booking.dto.PrescriptionItemResponse;
import com.vetclinic.booking.exception.PrescriptionNotPaidException;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.messaging.PrescriptionCreatedEvent;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.MedicalRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    // Upsert: bác sĩ có thể gọi lại nhiều lần để sửa chẩn đoán/đơn thuốc cho cùng 1 appointment,
    // không cần phân biệt create/update — đơn giản hơn vì mỗi appointment chỉ có tối đa 1 bệnh án.
    @Transactional
    public MedicalRecordResponse createOrUpdateMedicalRecord(UUID appointmentId, MedicalRecordRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        Optional<MedicalRecord> existing = medicalRecordRepository.findByAppointmentId(appointmentId);
        MedicalRecord record = existing.orElseGet(() -> MedicalRecord.builder().appointment(appointment).build());

        record.setDiagnosis(request.diagnosis());
        record.setTreatment(request.treatment());
        record.setNotes(request.notes());

        // clear() + add() trên chính collection đang persistent để Hibernate orphanRemoval xoá
        // đúng các item cũ — KHÔNG gán lại list mới, sẽ mất tracking của Hibernate.
        record.getPrescriptionItems().clear();
        if (request.prescriptionItems() != null) {
            request.prescriptionItems().forEach(itemRequest -> record.getPrescriptionItems().add(
                    PrescriptionItem.builder()
                            .medicalRecord(record)
                            .medicationName(itemRequest.medicationName())
                            .dosage(itemRequest.dosage())
                            .frequency(itemRequest.frequency())
                            .durationDays(itemRequest.durationDays())
                            .notes(itemRequest.notes())
                            .build()));
        }

        medicalRecordRepository.saveAndFlush(record);

        // Chỉ yêu cầu thanh toán ở lần TẠO MỚI — bác sĩ sửa lại chẩn đoán/đơn thuốc sau đó (vẫn
        // đang PENDING, chưa thanh toán) không nên bắn thêm yêu cầu thanh toán trùng lặp.
        if (existing.isEmpty()) {
            applicationEventPublisher.publishEvent(new PrescriptionCreatedEvent(record.getId(), appointmentId,
                    appointment.getCustomerUserId(), record.getPrescriptionItems().stream()
                            .map(item -> new PrescriptionCreatedEvent.Item(item.getMedicationName(), item.getDosage(),
                                    item.getFrequency(), item.getDurationDays()))
                            .toList()));
        }

        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public MedicalRecordResponse getMedicalRecord(UUID appointmentId, UUID callerUserId, boolean privileged) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        if (!privileged && !appointment.getCustomerUserId().equals(callerUserId)) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }

        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Medical record not found for appointment: " + appointmentId));
        return toResponse(record);
    }

    // Staff tiếp nhận đơn thuốc sau khi khách hàng ĐÃ THANH TOÁN (PAID -> RECEIVED) — idempotent,
    // gọi lại khi đã RECEIVED thì không lỗi, chỉ trả về đúng trạng thái hiện tại. Còn PENDING
    // (chưa thanh toán) thì chặn lại, không cho tiếp nhận.
    @Transactional
    public MedicalRecordResponse receivePrescription(UUID appointmentId) {
        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Medical record not found for appointment: " + appointmentId));

        if (record.getStatus() == PrescriptionStatus.PENDING) {
            throw new PrescriptionNotPaidException(
                    "Prescription not paid yet for appointment: " + appointmentId);
        }

        if (record.getStatus() != PrescriptionStatus.RECEIVED) {
            record.setStatus(PrescriptionStatus.RECEIVED);
            medicalRecordRepository.saveAndFlush(record);
        }

        return toResponse(record);
    }

    // Gọi khi nhận được event payment.completed (xem PaymentEventListener) — chuyển thẳng
    // PENDING -> RECEIVED. Phòng khám thu tiền và giao thuốc cùng một lượt ở quầy, không có
    // bước "chờ tiếp nhận" riêng như nhiều hiệu thuốc lớn — nên khách vừa trả tiền xong là
    // coi như đã nhận thuốc luôn, không cần staff xác nhận thêm một lần nữa qua
    // receivePrescription(). Bỏ qua nếu không tìm thấy record hoặc đã ở trạng thái khác PENDING
    // (đã xử lý rồi / message bị gửi lặp), không throw để không làm message bị requeue vô hạn.
    @Transactional
    public void markPrescriptionPaid(UUID medicalRecordId) {
        Optional<MedicalRecord> record = medicalRecordRepository.findById(medicalRecordId);
        if (record.isEmpty()) {
            log.warn("payment.completed event for unknown medicalRecordId={}", medicalRecordId);
            return;
        }

        if (record.get().getStatus() == PrescriptionStatus.PENDING) {
            record.get().setStatus(PrescriptionStatus.RECEIVED);
            medicalRecordRepository.saveAndFlush(record.get());
            log.info("Prescription paid and received, medicalRecordId={}", medicalRecordId);
        }
    }

    // Hàng đợi cho staff: đơn thuốc ĐÃ THANH TOÁN, đang chờ tiếp nhận — cũ nhất trước.
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listPendingPrescriptions() {
        return medicalRecordRepository.findByStatusOrderByCreatedAtAsc(PrescriptionStatus.PAID).stream()
                .map(this::toResponse)
                .toList();
    }

    // "Bệnh án còn treo" của bác sĩ: đơn thuốc CHÍNH BÁC SĨ NÀY kê mà còn dở — PENDING (khách
    // chưa trả tiền) hoặc PAID (đã trả nhưng staff chưa phát thuốc). Khác hẳn
    // listPendingPrescriptions() ở trên, vốn là hàng đợi PAID dùng chung mọi bác sĩ, chỉ staff xem.
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listMyOutstandingPrescriptions(UUID doctorId) {
        return medicalRecordRepository.findByStatusInAndDoctorUserId(
                        List.of(PrescriptionStatus.PENDING, PrescriptionStatus.PAID), doctorId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Lịch sử khám của 1 thú cưng, qua mọi appointment — mới nhất trước. Dùng cho màn tra
    // thú cưng khi bác sĩ/nhân viên muốn xem lại các lần khám trước, không chỉ ca hiện tại.
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listByPet(UUID petId) {
        return medicalRecordRepository.findByAppointment_PetIdOrderByCreatedAtDesc(petId).stream()
                .map(this::toResponse)
                .toList();
    }

    private MedicalRecordResponse toResponse(MedicalRecord record) {
        return new MedicalRecordResponse(record.getId(), record.getAppointment().getId(), record.getDiagnosis(),
                record.getTreatment(), record.getNotes(), record.getStatus(),
                record.getPrescriptionItems().stream().map(this::toItemResponse).toList(),
                record.getCreatedAt(), record.getUpdatedAt());
    }

    private PrescriptionItemResponse toItemResponse(PrescriptionItem item) {
        return new PrescriptionItemResponse(item.getId(), item.getMedicationName(), item.getDosage(),
                item.getFrequency(), item.getDurationDays(), item.getNotes());
    }
}
