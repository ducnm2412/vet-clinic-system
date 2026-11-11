package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.DoctorAvailabilityResponse;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.DoctorShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Admin xếp các yêu cầu đặt lịch của khách vào bác sĩ.
 *
 * Xếp bác sĩ nghĩa là gắn lịch vào khung giờ (slot) của bác sĩ đó, tạo slot nếu chưa có. Từ đó
 * lịch là một lịch thường như đặt kiểu cũ: trang của bác sĩ, thống kê và việc huỷ (trả slot) đều
 * chạy bằng code sẵn có.
 *
 * Giờ đề xuất cũng giữ sẵn slot của bác sĩ ngay lúc đề xuất (trạng thái chờ khách đồng ý). Không
 * giữ thì trong lúc khách cân nhắc, bác sĩ đó có thể bị xếp việc khác và lời đề xuất thành vô nghĩa.
 * Khách từ chối thì huỷ lịch, slot tự được trả.
 *
 * Lịch trực không bắt buộc: bác sĩ ngoài ca vẫn xếp được, chỉ được đánh dấu để admin biết.
 */
@Service
@RequiredArgsConstructor
public class AppointmentAssignmentService {

    private static final List<AppointmentStatus> AWAITING =
            List.of(AppointmentStatus.AWAITING_ASSIGNMENT, AppointmentStatus.AWAITING_CUSTOMER);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final DoctorShiftRepository doctorShiftRepository;
    private final DoctorDirectory doctorDirectory;
    private final AppointmentRequestService requestService;
    private final AppointmentService appointmentService;
    private final AppointmentNotifier notifier;

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAwaiting() {
        return appointmentRepository.findAwaiting(AWAITING).stream().map(appointmentService::toResponse).toList();
    }

    /**
     * Bác sĩ nào xếp được vào lịch này, vào giờ {@code date}/{@code time} (mặc định là giờ khách
     * mong muốn). Trả cả bác sĩ đang bận kèm cờ {@code free=false} để admin thấy vì sao không chọn được.
     */
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> availableDoctors(UUID appointmentId, LocalDate date, LocalTime time) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
        LocalDate day = date != null ? date : appointment.getPreferredDate();
        LocalTime at = time != null ? time : appointment.getPreferredTime();
        if (day == null || at == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu ngày hoặc giờ cần xem");
        }
        requestService.requireBookableTime(day, at);

        return doctorDirectory.activeDoctors().stream()
                .map(d -> toAvailability(d, day, at, appointment))
                .sorted(Comparator.comparing(DoctorAvailabilityResponse::free).reversed()
                        .thenComparing(Comparator.comparing(DoctorAvailabilityResponse::onShift).reversed())
                        .thenComparingLong(DoctorAvailabilityResponse::dayLoad)
                        .thenComparing(d -> d.fullName() == null ? "" : d.fullName()))
                .toList();
    }

    /** Xếp bác sĩ đúng giờ khách mong muốn: lịch thành CONFIRMED. */
    @Transactional
    public AppointmentResponse assign(UUID appointmentId, UUID doctorUserId) {
        Appointment appointment = lockedAppointment(appointmentId);
        if (appointment.getStatus() != AppointmentStatus.AWAITING_ASSIGNMENT) {
            throw conflict("Lịch này không còn ở trạng thái chờ xếp bác sĩ.");
        }
        requireActiveDoctor(doctorUserId);

        LocalDate date = appointment.getPreferredDate();
        LocalTime time = appointment.getPreferredTime();
        // Chỉ cần giờ chưa trôi qua: lúc khách gửi, giờ đã được kiểm tra hợp lệ rồi.
        LocalDate today = LocalDate.now(ClinicSchedule.ZONE_ID);
        if (date.isBefore(today)
                || (date.isEqual(today) && !time.isAfter(LocalTime.now(ClinicSchedule.ZONE_ID)))) {
            throw conflict("Giờ khách chọn đã qua, hãy đề xuất giờ khác hoặc huỷ lịch.");
        }

        appointmentRepository.advisoryLock(AppointmentRequestService.lockKey(date, time));
        AppointmentSlot slot = claimSlot(doctorUserId, date, time, appointment);

        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.saveAndFlush(appointment);
        notifier.confirmed(appointment);
        return appointmentService.toResponse(appointment);
    }

    /**
     * Đề xuất giờ khác (và bác sĩ rảnh giờ đó) khi không xếp được đúng giờ khách chọn. Lịch chuyển
     * sang chờ khách đồng ý; đề xuất lại lần nữa thì đổi lời đề xuất cũ.
     */
    @Transactional
    public AppointmentResponse propose(UUID appointmentId, UUID doctorUserId, LocalDate date, LocalTime time) {
        Appointment appointment = lockedAppointment(appointmentId);
        if (!AWAITING.contains(appointment.getStatus())) {
            throw conflict("Chỉ đề xuất giờ khác được cho lịch đang chờ xếp bác sĩ hoặc chờ khách đồng ý.");
        }
        if (date.equals(appointment.getPreferredDate()) && time.equals(appointment.getPreferredTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giờ này trùng giờ khách đã chọn — hãy xếp bác sĩ thẳng thay vì đề xuất.");
        }
        requestService.requireBookableTime(date, time);
        requireActiveDoctor(doctorUserId);

        appointmentRepository.advisoryLock(AppointmentRequestService.lockKey(date, time));

        // Giờ đề xuất cũng chiếm chỗ như một yêu cầu: để quá số bác sĩ thì các yêu cầu khác đang chờ
        // ở giờ đó sẽ không còn ai để xếp. Lịch này tự đang chiếm chỗ ở giờ cũ của nó, không tính hai lần.
        boolean occupiesTarget = date.equals(appointment.effectiveDate()) && time.equals(appointment.effectiveStartTime());
        long self = occupiesTarget ? 1 : 0;
        if (appointmentRepository.countOccupying(date, time) - self >= doctorDirectory.activeDoctorCount()) {
            throw conflict("Giờ này đã đủ lịch, không đề xuất thêm được.");
        }
        if (appointmentRepository.countOccupyingForPet(appointment.getPetId(), date, time) - self > 0) {
            throw conflict("Bé này đã có lịch khám vào giờ đó.");
        }

        AppointmentSlot previous = appointment.getSlot();
        AppointmentSlot slot = claimSlot(doctorUserId, date, time, appointment);
        if (previous != null && !previous.getId().equals(slot.getId())) {
            releaseSlot(previous);
        }

        appointment.setSlot(slot);
        appointment.setProposedDate(date);
        appointment.setProposedTime(time);
        appointment.setStatus(AppointmentStatus.AWAITING_CUSTOMER);
        appointmentRepository.saveAndFlush(appointment);
        notifier.proposal(appointment);
        return appointmentService.toResponse(appointment);
    }

    /** Khách đồng ý giờ phòng khám đề xuất: lịch thành CONFIRMED, slot đã giữ sẵn từ lúc đề xuất. */
    @Transactional
    public AppointmentResponse acceptProposal(UUID appointmentId, UUID customerUserId) {
        Appointment appointment = lockedAppointment(appointmentId);
        // Không phải chủ thì báo không tìm thấy, như huỷ lịch — không lộ lịch đó có tồn tại.
        if (!appointment.getCustomerUserId().equals(customerUserId)) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }
        if (appointment.getStatus() != AppointmentStatus.AWAITING_CUSTOMER || appointment.getSlot() == null) {
            throw conflict("Lịch này không có giờ đề xuất nào đang chờ bạn đồng ý.");
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.saveAndFlush(appointment);
        notifier.confirmed(appointment);
        return appointmentService.toResponse(appointment);
    }

    // ---- nội bộ ----

    private Appointment lockedAppointment(UUID appointmentId) {
        return appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
    }

    private void requireActiveDoctor(UUID doctorUserId) {
        boolean active = doctorDirectory.activeDoctors().stream().anyMatch(d -> doctorUserId.equals(d.userId()));
        if (!active) {
            throw conflict("Bác sĩ này không nhận được lịch (không có trong danh sách hoặc đang bị khoá).");
        }
    }

    /**
     * Giữ slot của bác sĩ ở giờ này cho lịch {@code owner}: dùng slot sẵn có nếu đang trống, tạo mới
     * nếu chưa có, từ chối nếu bác sĩ đã có lịch khác. Chính slot lịch này đang giữ không tính là bận.
     * Gọi sau khi đã khoá theo khung giờ.
     */
    private AppointmentSlot claimSlot(UUID doctorUserId, LocalDate date, LocalTime time, Appointment owner) {
        AppointmentSlot slot = slotRepository.findByDoctorUserIdAndDate(doctorUserId, date).stream()
                .filter(s -> s.getStartTime().equals(time)).findFirst().orElse(null);

        if (slot == null) {
            slot = AppointmentSlot.builder().doctorUserId(doctorUserId).date(date).startTime(time)
                    .endTime(time.plusMinutes(ClinicSchedule.SLOT_DURATION_MINUTES)).build();
        } else if (slot.getStatus() != SlotStatus.AVAILABLE) {
            boolean heldByOwner = owner.getSlot() != null && owner.getSlot().getId().equals(slot.getId());
            if (!heldByOwner) {
                throw conflict("Bác sĩ đã có lịch khám vào giờ này.");
            }
        }
        slot.setStatus(SlotStatus.BOOKED);
        return slotRepository.saveAndFlush(slot);
    }

    private void releaseSlot(AppointmentSlot slot) {
        slot.setStatus(SlotStatus.AVAILABLE);
        slotRepository.saveAndFlush(slot);
    }

    private DoctorAvailabilityResponse toAvailability(DoctorSummaryResponse doctor, LocalDate date, LocalTime time,
                                                      Appointment appointment) {
        UUID id = doctor.userId();
        UUID heldSlotId = appointment.getSlot() == null ? null : appointment.getSlot().getId();
        boolean busy = slotRepository.findByDoctorUserIdAndDate(id, date).stream()
                .anyMatch(s -> s.getStartTime().equals(time) && s.getStatus() != SlotStatus.AVAILABLE
                        && !s.getId().equals(heldSlotId));
        boolean onShift = doctorShiftRepository.findByDoctorUserIdAndDate(id, date).stream()
                .anyMatch(shift -> shift.covers(time));
        long load = appointmentRepository.countActiveAppointmentsForDoctorOnDate(id, date);
        return new DoctorAvailabilityResponse(id, doctor.fullName(), doctor.specialty(), !busy, onShift, load);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
