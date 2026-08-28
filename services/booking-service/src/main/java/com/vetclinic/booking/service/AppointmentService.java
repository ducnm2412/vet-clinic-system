package com.vetclinic.booking.service;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentDetailResponse;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.dto.SuggestedSlotResponse;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.exception.SlotFullyBookedException;
import com.vetclinic.booking.messaging.AppointmentCreatedEvent;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProfileServiceClient profileServiceClient;
    private final SuggestionService suggestionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public AppointmentResponse createAppointment(UUID customerUserId, String bearerToken, AppointmentRequest request) {
        validateOwnsPet(request.petId(), bearerToken);

        List<AppointmentSlot> available = appointmentSlotRepository.findAvailableForUpdate(
                request.date(), request.startTime());

        if (available.isEmpty()) {
            List<SuggestedSlotResponse> suggestions = suggestionService.findSuggestions(
                    request.date(), request.startTime());
            throw new SlotFullyBookedException(suggestions);
        }

        // Auto-assign: nếu nhiều bác sĩ cùng rảnh đúng khung giờ này, chọn bác sĩ có ít lịch hẹn
        // active nhất trong ngày (cân bằng tải) — ORDER BY doctorUserId ở repository chỉ còn tác
        // dụng tie-break deterministic khi có từ 2 bác sĩ trở lên bằng số lịch nhau.
        AppointmentSlot slot = available.stream()
                .min(Comparator.comparingLong(s -> appointmentRepository.countActiveAppointmentsForDoctorOnDate(
                        s.getDoctorUserId(), s.getDate())))
                .orElseThrow();
        slot.setStatus(SlotStatus.BOOKED);
        appointmentSlotRepository.saveAndFlush(slot);

        Appointment appointment = Appointment.builder()
                .slot(slot)
                .customerUserId(customerUserId)
                .petId(request.petId())
                .reason(request.reason())
                .build();

        appointmentRepository.saveAndFlush(appointment);

        applicationEventPublisher.publishEvent(new AppointmentCreatedEvent(appointment.getId(), slot.getId(),
                slot.getDoctorUserId(), appointment.getCustomerUserId(), appointment.getPetId(), slot.getDate(),
                slot.getStartTime(), slot.getEndTime()));

        return toResponse(appointment);
    }

    // DOCTOR/STAFF/ADMIN xem chi tiết 1 appointment bất kỳ, enrich thêm thông tin pet qua
    // getPetById (bước 6) — dùng chính token của người gọi vì route đó vốn đã yêu cầu 1 trong
    // 3 role này bên profile-service.
    @Transactional(readOnly = true)
    public AppointmentDetailResponse getAppointmentDetail(UUID appointmentId, String bearerToken) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        PetResponse pet = profileServiceClient.getPetById(appointment.getPetId(), bearerToken);
        return toDetailResponse(appointment, pet);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listMyAppointments(UUID customerUserId) {
        return appointmentRepository.findByCustomerUserId(customerUserId).stream().map(this::toResponse).toList();
    }

    // date/status/doctorUserId đều optional — bác sĩ tự xem lịch của mình truyền doctorUserId =
    // userId của chính họ, staff/admin lọc toàn bộ theo tham số tự chọn.
    @Transactional(readOnly = true)
    public List<AppointmentResponse> search(LocalDate date, AppointmentStatus status, UUID doctorUserId) {
        return appointmentRepository.search(date, status, doctorUserId).stream().map(this::toResponse).toList();
    }

    // privileged=true (STAFF/ADMIN): huỷ bất kỳ appointment nào. privileged=false (CUSTOMER):
    // chỉ huỷ được appointment của chính mình — không phải chủ thì báo not-found (không phải
    // forbidden) để không lộ appointment đó có tồn tại hay không, giống pattern ownership check
    // của profile-service.
    @Transactional
    public AppointmentResponse cancelAppointment(UUID appointmentId, UUID callerUserId, boolean privileged) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        if (!privileged && !appointment.getCustomerUserId().equals(callerUserId)) {
            throw new ResourceNotFoundException("Appointment not found: " + appointmentId);
        }

        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            AppointmentSlot slot = appointment.getSlot();
            slot.setStatus(SlotStatus.AVAILABLE);
            appointmentSlotRepository.saveAndFlush(slot);
            appointmentRepository.saveAndFlush(appointment);
        }

        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse updateStatus(UUID appointmentId, AppointmentStatus newStatus) {
        Appointment appointment = getAppointmentOrThrow(appointmentId);
        appointment.setStatus(newStatus);
        appointmentRepository.saveAndFlush(appointment);
        return toResponse(appointment);
    }

    private Appointment getAppointmentOrThrow(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));
    }

    private void validateOwnsPet(UUID petId, String bearerToken) {
        try {
            profileServiceClient.getMyPet(petId, bearerToken);
        } catch (FeignException.NotFound | FeignException.Forbidden e) {
            throw new ResourceNotFoundException("Pet not found or not owned by customer: " + petId);
        }
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        AppointmentSlot slot = appointment.getSlot();
        return new AppointmentResponse(appointment.getId(), slot.getId(), slot.getDate(), slot.getStartTime(),
                slot.getEndTime(), appointment.getCustomerUserId(), appointment.getPetId(), appointment.getReason(),
                appointment.getStatus(), appointment.getCreatedAt(), appointment.getUpdatedAt());
    }

    private AppointmentDetailResponse toDetailResponse(Appointment appointment, PetResponse pet) {
        AppointmentSlot slot = appointment.getSlot();
        return new AppointmentDetailResponse(appointment.getId(), slot.getId(), slot.getDate(), slot.getStartTime(),
                slot.getEndTime(), appointment.getCustomerUserId(), appointment.getPetId(), appointment.getReason(),
                appointment.getStatus(), appointment.getCreatedAt(), appointment.getUpdatedAt(), pet);
    }
}
