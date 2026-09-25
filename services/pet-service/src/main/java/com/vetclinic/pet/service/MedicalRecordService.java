package com.vetclinic.pet.service;

import com.vetclinic.pet.client.BookingServiceClient;
import com.vetclinic.pet.domain.MedicalRecord;
import com.vetclinic.pet.domain.PrescriptionItem;
import com.vetclinic.pet.domain.PrescriptionStatus;
import com.vetclinic.pet.dto.MedicalRecordRequest;
import com.vetclinic.pet.dto.MedicalRecordResponse;
import com.vetclinic.pet.dto.PrescriptionItemResponse;
import com.vetclinic.pet.exception.BookingUnavailableException;
import com.vetclinic.pet.exception.PrescriptionNotPaidException;
import com.vetclinic.pet.exception.ResourceNotFoundException;
import com.vetclinic.pet.messaging.PrescriptionCreatedEvent;
import com.vetclinic.pet.repository.MedicalRecordRepository;
import com.vetclinic.pet.repository.PetRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CN-23 → CN-25: bệnh án và đơn thuốc. Chuyển từ booking-service sang (VD-10 chặng 2).
 *
 * Chỗ khác biệt lớn nhất so với bản cũ: không còn join sang bảng lịch hẹn. Lúc bác sĩ lập bệnh án,
 * service hỏi booking-service một lần để biết lịch hẹn có thật và của ai, rồi chép petId, chủ nuôi
 * và bác sĩ vào bệnh án. Mọi lần đọc sau đó không cần booking-service nữa — bệnh án là hồ sơ lâm
 * sàng, phải tra được cả khi service kia đang tắt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final PetRepository petRepository;
    private final BookingServiceClient bookingServiceClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Upsert: bác sĩ gọi lại nhiều lần để sửa chẩn đoán hay đơn thuốc cho cùng một lịch hẹn, không
     * cần phân biệt tạo và sửa — mỗi lịch hẹn có tối đa một bệnh án.
     */
    @Transactional
    public MedicalRecordResponse createOrUpdate(UUID appointmentId, MedicalRecordRequest request,
                                               String bearerToken) {
        Optional<MedicalRecord> existing = medicalRecordRepository.findByAppointmentId(appointmentId);
        // Bác sĩ nào cũng lập và sửa được, y như bản cũ bên booking-service: trong phòng khám nhỏ
        // bác sĩ trực thay nhau là chuyện thường. Người khám vẫn là bác sĩ ghi trong lịch hẹn
        // (doctorUserId chép từ đó), không phải người bấm lưu.
        MedicalRecord record = existing.orElseGet(() -> newRecordFor(appointmentId, bearerToken));

        record.setDiagnosis(request.diagnosis().trim());
        record.setTreatment(request.treatment());
        record.setNotes(request.notes());

        // clear() + add() trên chính collection đang persistent để orphanRemoval xoá đúng các dòng
        // thuốc cũ — KHÔNG gán list mới, Hibernate sẽ mất tracking.
        record.getPrescriptionItems().clear();
        if (request.prescriptionItems() != null) {
            request.prescriptionItems().forEach(item -> record.getPrescriptionItems().add(
                    PrescriptionItem.builder()
                            .medicalRecord(record)
                            .medicationName(item.medicationName())
                            .dosage(item.dosage())
                            .frequency(item.frequency())
                            .durationDays(item.durationDays())
                            .notes(item.notes())
                            .build()));
        }

        medicalRecordRepository.saveAndFlush(record);

        // Chỉ yêu cầu thanh toán ở lần tạo mới: bác sĩ sửa lại đơn khi khách còn chưa trả tiền
        // không được bắn thêm một phiếu thu trùng.
        if (existing.isEmpty()) {
            applicationEventPublisher.publishEvent(PrescriptionCreatedEvent.of(record));
        }

        return toResponse(record);
    }

    /** Lịch hẹn phải có thật, và con vật trong lịch hẹn phải là con service này đang giữ hồ sơ. */
    private MedicalRecord newRecordFor(UUID appointmentId, String bearerToken) {
        BookingServiceClient.AppointmentRef appointment = fetchAppointment(appointmentId, bearerToken);

        if (!petRepository.existsById(appointment.petId())) {
            throw new ResourceNotFoundException("Không tìm thấy thú cưng: " + appointment.petId());
        }

        return MedicalRecord.builder()
                .appointmentId(appointmentId)
                .petId(appointment.petId())
                .customerUserId(appointment.customerUserId())
                .doctorUserId(appointment.doctorUserId())
                .build();
    }

    private BookingServiceClient.AppointmentRef fetchAppointment(UUID appointmentId, String bearerToken) {
        try {
            return bookingServiceClient.getAppointment(appointmentId, bearerToken);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Không tìm thấy lịch hẹn: " + appointmentId);
        } catch (FeignException e) {
            // 503 chứ không 500: lỗi ở đây là "chưa hỏi được", không phải "dữ liệu sai".
            log.warn("Không hỏi được booking-service về lịch hẹn {}: {}", appointmentId, e.getMessage());
            throw new BookingUnavailableException(
                    "Chưa kiểm tra được lịch hẹn vì booking-service không trả lời. Thử lại sau.");
        }
    }

    /**
     * Khách chỉ xem được bệnh án của chính mình; bác sĩ, nhân viên và admin xem được mọi bệnh án.
     * Không phải của mình thì "không tìm thấy" — 403 tự xác nhận bệnh án đó có thật.
     */
    @Transactional(readOnly = true)
    public MedicalRecordResponse getByAppointment(UUID appointmentId, UUID callerUserId, boolean privileged) {
        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bệnh án của lịch hẹn: " + appointmentId));

        if (!privileged && !record.getCustomerUserId().equals(callerUserId)) {
            throw new ResourceNotFoundException("Không tìm thấy bệnh án của lịch hẹn: " + appointmentId);
        }

        return toResponse(record);
    }

    /** CN-24: lịch sử khám của một con vật, mới nhất trước. Dùng cho người trong phòng khám. */
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> historyOfPet(UUID petId) {
        if (!petRepository.existsById(petId)) {
            throw new ResourceNotFoundException("Không tìm thấy thú cưng: " + petId);
        }
        return medicalRecordRepository.findByPetIdOrderByCreatedAtDesc(petId).stream().map(this::toResponse).toList();
    }

    /** CN-24 cho khách: lịch sử khám của chính con vật mình nuôi. */
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> historyOfMyPet(UUID ownerUserId, UUID petId) {
        petRepository.findByIdAndOwnerUserId(petId, ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thú cưng: " + petId));
        return medicalRecordRepository.findByPetIdOrderByCreatedAtDesc(petId).stream().map(this::toResponse).toList();
    }

    /**
     * Quầy giao thuốc sau khi khách đã trả tiền (PAID → RECEIVED). Gọi lại khi đã RECEIVED thì
     * không lỗi, chỉ trả về trạng thái hiện tại. Còn PENDING thì chặn.
     */
    @Transactional
    public MedicalRecordResponse receive(UUID appointmentId) {
        MedicalRecord record = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bệnh án của lịch hẹn: " + appointmentId));

        if (record.getStatus() == PrescriptionStatus.PENDING) {
            throw new PrescriptionNotPaidException("Đơn thuốc của lịch hẹn " + appointmentId
                    + " chưa được thanh toán, chưa giao thuốc được.");
        }

        if (record.getStatus() != PrescriptionStatus.RECEIVED) {
            record.setStatus(PrescriptionStatus.RECEIVED);
            medicalRecordRepository.saveAndFlush(record);
        }

        return toResponse(record);
    }

    /**
     * Gọi khi nhận payment.completed. Chuyển thẳng PENDING → RECEIVED: phòng khám thu tiền và giao
     * thuốc cùng một lượt ở quầy, không có bước chờ tiếp nhận riêng.
     *
     * Không tìm thấy hoặc đã ở trạng thái khác PENDING (message gửi lặp) thì bỏ qua, không throw —
     * throw sẽ làm message bị requeue vô hạn.
     */
    @Transactional
    public void markPaid(UUID medicalRecordId) {
        Optional<MedicalRecord> record = medicalRecordRepository.findById(medicalRecordId);
        if (record.isEmpty()) {
            log.warn("payment.completed cho medicalRecordId={} không tồn tại", medicalRecordId);
            return;
        }

        if (record.get().getStatus() == PrescriptionStatus.PENDING) {
            record.get().setStatus(PrescriptionStatus.RECEIVED);
            medicalRecordRepository.saveAndFlush(record.get());
            log.info("Đơn thuốc {} đã thanh toán và giao thuốc", medicalRecordId);
        }
    }

    /** Hàng đợi của quầy: đơn thuốc đã thanh toán, chờ giao — cũ nhất trước. */
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listPaidQueue() {
        return medicalRecordRepository.findByStatusOrderByCreatedAtAsc(PrescriptionStatus.PAID).stream()
                .map(this::toResponse).toList();
    }

    /** "Bệnh án còn treo" của một bác sĩ: chính bác sĩ này kê mà còn PENDING hoặc PAID. */
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> listOutstandingOf(UUID doctorUserId) {
        return medicalRecordRepository.findByDoctorUserIdAndStatusInOrderByCreatedAtAsc(doctorUserId,
                        List.of(PrescriptionStatus.PENDING, PrescriptionStatus.PAID)).stream()
                .map(this::toResponse).toList();
    }

    private MedicalRecordResponse toResponse(MedicalRecord record) {
        return new MedicalRecordResponse(record.getId(), record.getAppointmentId(), record.getPetId(),
                record.getCustomerUserId(), record.getDoctorUserId(), record.getDiagnosis(), record.getTreatment(),
                record.getNotes(), record.getStatus(),
                record.getPrescriptionItems().stream().map(MedicalRecordService::toItemResponse).toList(),
                record.getCreatedAt(), record.getUpdatedAt());
    }

    private static PrescriptionItemResponse toItemResponse(PrescriptionItem item) {
        return new PrescriptionItemResponse(item.getId(), item.getMedicationName(), item.getDosage(),
                item.getFrequency(), item.getDurationDays(), item.getNotes());
    }
}
