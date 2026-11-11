package com.vetclinic.booking.service;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.ClinicHourResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.exception.ResourceNotFoundException;
import com.vetclinic.booking.repository.AppointmentRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Đặt lịch theo yêu cầu: khách chỉ nói giờ mong muốn, chưa có bác sĩ và chưa giữ slot nào. Admin
 * xếp bác sĩ sau (bước tiếp theo).
 *
 * Vì không có slot để khoá, "còn chỗ hay không" phải tự tính: mỗi bác sĩ khám một ca một lúc, nên
 * một khung giờ nhận tối đa bằng số bác sĩ đang làm việc. Lịch tới trước đó (đã xếp hoặc đang chờ)
 * đều chiếm một chỗ. Nhận nhiều hơn thì admin chắc chắn không xếp hết được.
 *
 * Lịch trực (doctor_shifts) không ảnh hưởng chuyện này: nó chỉ giúp admin biết ai có ca, không
 * chặn khách gửi yêu cầu.
 */
@Service
@RequiredArgsConstructor
public class AppointmentRequestService {

    static final List<AppointmentStatus> OPEN_STATUSES =
            List.of(AppointmentStatus.AWAITING_ASSIGNMENT, AppointmentStatus.AWAITING_CUSTOMER);

    private final AppointmentRepository appointmentRepository;
    private final DoctorDirectory doctorDirectory;
    private final PetServiceClient petServiceClient;
    private final ClinicServiceCatalog clinicServiceCatalog;
    private final AppointmentService appointmentService;
    private final AppointmentNotifier notifier;

    @Transactional
    public AppointmentResponse createRequest(UUID customerUserId, String bearerToken, AppointmentRequest request) {
        return createRequest(customerUserId, null, bearerToken, request);
    }

    /** `customerEmail` lấy từ JWT của khách; có thì mới báo được khách qua email khi admin xếp lịch. */
    @Transactional
    public AppointmentResponse createRequest(UUID customerUserId, String customerEmail, String bearerToken,
                                             AppointmentRequest request) {
        LocalDate date = request.date();
        LocalTime time = request.startTime();
        requireBookableTime(date, time);

        PetResponse pet = requireOwnsPet(request.petId(), bearerToken);
        var service = clinicServiceCatalog.requireBookable(request.serviceId());

        // Khoá theo khách (giới hạn số yêu cầu đang mở) rồi theo khung giờ (sức chứa). Mọi lời gọi
        // đều khoá theo đúng thứ tự này nên không bao giờ chờ vòng tròn nhau.
        appointmentRepository.advisoryLock(lockKey(customerUserId));
        appointmentRepository.advisoryLock(lockKey(date, time));

        if (appointmentRepository.countByCustomerUserIdAndStatusIn(customerUserId, OPEN_STATUSES)
                >= ClinicSchedule.MAX_OPEN_REQUESTS_PER_CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đang có "
                    + ClinicSchedule.MAX_OPEN_REQUESTS_PER_CUSTOMER
                    + " yêu cầu chờ phòng khám xử lý. Đợi phòng khám xác nhận bớt rồi hãy gửi thêm.");
        }

        long doctors = doctorDirectory.activeDoctorCount();
        if (doctors == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng khám hiện chưa có bác sĩ nhận lịch. Bạn vui lòng gọi phòng khám để được hỗ trợ.");
        }
        if (appointmentRepository.countOccupyingForPet(request.petId(), date, time) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bé này đã có lịch khám vào giờ đó.");
        }
        if (appointmentRepository.countOccupying(date, time) >= doctors) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khung giờ này đã đủ lịch. Bạn chọn giờ khác giúp phòng khám nhé.");
        }

        Appointment appointment = Appointment.builder()
                .customerUserId(customerUserId)
                .petId(request.petId())
                .service(service)
                .reason(request.reason())
                .customerEmail(customerEmail)
                .petName(pet == null ? null : pet.name())
                .status(AppointmentStatus.AWAITING_ASSIGNMENT)
                .preferredDate(date)
                .preferredTime(time)
                .build();
        appointmentRepository.saveAndFlush(appointment);
        notifier.requestReceived(appointment);
        return appointmentService.toResponse(appointment);
    }

    /**
     * Các mốc giờ khách chọn được trong ngày, kèm số chỗ còn lại. Ngoài khoảng đặt được (quá khứ
     * hoặc quá xa) trả danh sách rỗng chứ không báo lỗi — màn hình chỉ cần hiện "chưa nhận".
     */
    @Transactional(readOnly = true)
    public List<ClinicHourResponse> clinicHours(LocalDate date) {
        LocalDate today = LocalDate.now(ClinicSchedule.ZONE_ID);
        if (date.isBefore(today) || !date.isBefore(today.plusDays(ClinicSchedule.REQUEST_HORIZON_DAYS))) {
            return List.of();
        }
        long doctors = doctorDirectory.activeDoctorCount();
        LocalTime now = LocalTime.now(ClinicSchedule.ZONE_ID);

        return ClinicSchedule.SLOT_TIMES.stream().map(time -> {
            boolean past = date.isEqual(today) && !time.isAfter(now);
            long remaining = past ? 0 : Math.max(0, doctors - appointmentRepository.countOccupying(date, time));
            return new ClinicHourResponse(time, time.plusMinutes(ClinicSchedule.SLOT_DURATION_MINUTES),
                    remaining > 0, remaining);
        }).toList();
    }

    /** Chặn giờ lạ, ngày đã qua, ngày quá xa — kể cả khi client gọi thẳng API bỏ qua màn hình. */
    void requireBookableTime(LocalDate date, LocalTime time) {
        if (!ClinicSchedule.SLOT_TIMES.contains(time)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ khám không nằm trong giờ làm việc");
        }
        LocalDate today = LocalDate.now(ClinicSchedule.ZONE_ID);
        if (date.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đặt được ngày đã qua");
        }
        if (!date.isBefore(today.plusDays(ClinicSchedule.REQUEST_HORIZON_DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ đặt trước được tối đa " + ClinicSchedule.REQUEST_HORIZON_DAYS + " ngày");
        }
        if (date.isEqual(today) && !time.isAfter(LocalTime.now(ClinicSchedule.ZONE_ID))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ này đã qua, bạn chọn giờ khác nhé");
        }
    }

    private PetResponse requireOwnsPet(UUID petId, String bearerToken) {
        try {
            return petServiceClient.getMyPet(petId, bearerToken);
        } catch (FeignException.NotFound | FeignException.Forbidden e) {
            throw new ResourceNotFoundException("Pet not found or not owned by customer: " + petId);
        }
    }

    /** Khoá theo khung giờ — dùng chung với việc xếp bác sĩ, vì hai bên cùng đổi "số chỗ đã chiếm". */
    static long lockKey(LocalDate date, LocalTime time) {
        return date.toEpochDay() * 100_000L + time.toSecondOfDay();
    }

    private static long lockKey(UUID customerUserId) {
        return customerUserId.getMostSignificantBits() ^ customerUserId.getLeastSignificantBits();
    }
}
