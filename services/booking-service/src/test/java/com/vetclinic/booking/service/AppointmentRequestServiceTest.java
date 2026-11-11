package com.vetclinic.booking.service;

import com.vetclinic.booking.client.PetServiceClient;
import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.Appointment;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.AppointmentStatus;
import com.vetclinic.booking.domain.BlockedDoctor;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.AppointmentStatsResponse;
import com.vetclinic.booking.dto.ClinicHourResponse;
import com.vetclinic.booking.dto.DoctorSummaryResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.repository.AppointmentRepository;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.BlockedDoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Đặt lịch theo yêu cầu. Mọi test chạy trong transaction và dùng ngày tính từ hôm nay (cửa sổ đặt
 * trước chỉ có {@link ClinicSchedule#REQUEST_HORIZON_DAYS} ngày nên không dùng ngày cố định được).
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class AppointmentRequestServiceTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);

    @Autowired
    private AppointmentRequestService requestService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AppointmentStatsService statsService;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private AppointmentSlotRepository slotRepository;
    @Autowired
    private BlockedDoctorRepository blockedDoctorRepository;

    @MockBean
    private PetServiceClient petServiceClient;
    @MockBean
    private ProfileServiceClient profileServiceClient;

    private final LocalDate day = LocalDate.now(ClinicSchedule.ZONE_ID).plusDays(10);

    @BeforeEach
    void defaults() {
        when(petServiceClient.getMyPet(any(), any())).thenReturn(pet());
        doctors(1);
    }

    @Test
    void request_isRecordedWithoutDoctorOrSlot() {
        UUID petId = UUID.randomUUID();
        AppointmentResponse r = requestService.createRequest(UUID.randomUUID(), "Bearer t",
                new AppointmentRequest(petId, day, NINE, null, "Ngứa tai"));

        assertThat(r.status()).isEqualTo(AppointmentStatus.AWAITING_ASSIGNMENT);
        assertThat(r.slotId()).isNull();
        assertThat(r.doctorUserId()).isNull();
        // Chưa có slot thì ngày/giờ trả về là giờ khách mong muốn, giờ kết thúc cộng 30 phút.
        assertThat(r.date()).isEqualTo(day);
        assertThat(r.startTime()).isEqualTo(NINE);
        assertThat(r.endTime()).isEqualTo(NINE.plusMinutes(30));
        assertThat(r.petId()).isEqualTo(petId);
        assertThat(r.proposedDate()).isNull();
    }

    @Test
    void request_worksEvenWhenNoDoctorHasAShift() {
        // Chính là lỗi gốc: chưa xếp ca nào thì khách vẫn gửi được yêu cầu.
        assertThat(slotRepository.findAll().stream().filter(sl -> sl.getDate().equals(day))).isEmpty();
        AppointmentResponse r = requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));
        assertThat(r.status()).isEqualTo(AppointmentStatus.AWAITING_ASSIGNMENT);
    }

    @Test
    void request_rejectsTimeOutsideClinicHours() {
        assertThatThrownBy(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t",
                req(LocalTime.of(12, 0))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        // 9:15 không phải mốc nửa giờ.
        assertThatThrownBy(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t",
                req(LocalTime.of(9, 15))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void request_rejectsPastAndTooFarDates() {
        LocalDate today = LocalDate.now(ClinicSchedule.ZONE_ID);
        assertBadRequest(today.minusDays(1), NINE);
        assertBadRequest(today.plusDays(ClinicSchedule.REQUEST_HORIZON_DAYS), NINE);
        // Ngày cuối còn nhận là hôm nay + (horizon - 1).
        AppointmentResponse last = requestService.createRequest(UUID.randomUUID(), "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), today.plusDays(ClinicSchedule.REQUEST_HORIZON_DAYS - 1),
                        NINE, null, null));
        assertThat(last.status()).isEqualTo(AppointmentStatus.AWAITING_ASSIGNMENT);
    }

    @Test
    void capacity_equalsNumberOfDoctors_perTimeSlot() {
        doctors(2);
        requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));
        requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));

        // Chỗ thứ ba cùng giờ: hai bác sĩ đã có hai lịch.
        assertConflict(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)));
        // Giờ khác vẫn nhận.
        assertThat(requestService.createRequest(UUID.randomUUID(), "Bearer t", req(LocalTime.of(9, 30))).status())
                .isEqualTo(AppointmentStatus.AWAITING_ASSIGNMENT);
    }

    @Test
    void capacity_ignoresBlockedDoctors() {
        List<DoctorSummaryResponse> two = doctors(2);
        blockedDoctorRepository.saveAndFlush(new BlockedDoctor(two.get(0).userId(), Instant.now()));

        requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));
        // Một bác sĩ bị khoá → chỉ còn một chỗ.
        assertConflict(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)));
    }

    @Test
    void capacity_zeroWhenNoDoctors() {
        doctors(0);
        assertConflict(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)));
    }

    @Test
    void slotBookedAppointment_occupiesCapacityToo() {
        // Lịch đặt kiểu cũ (qua slot) cũng chiếm một chỗ của khung giờ đó.
        AppointmentSlot slot = slotRepository.saveAndFlush(AppointmentSlot.builder()
                .doctorUserId(UUID.randomUUID()).date(day).startTime(NINE).endTime(NINE.plusMinutes(30))
                .status(SlotStatus.BOOKED).build());
        appointmentRepository.saveAndFlush(Appointment.builder()
                .slot(slot).customerUserId(UUID.randomUUID()).petId(UUID.randomUUID()).build());

        assertConflict(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)));
    }

    @Test
    void samePet_cannotHaveTwoAppointmentsAtOnce() {
        doctors(3);
        UUID petId = UUID.randomUUID();
        requestService.createRequest(UUID.randomUUID(), "Bearer t",
                new AppointmentRequest(petId, day, NINE, null, null));

        assertConflict(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t",
                new AppointmentRequest(petId, day, NINE, null, null)));
    }

    @Test
    void customer_isLimitedToThreeOpenRequests() {
        doctors(10);
        UUID customer = UUID.randomUUID();
        for (int i = 0; i < ClinicSchedule.MAX_OPEN_REQUESTS_PER_CUSTOMER; i++) {
            requestService.createRequest(customer, "Bearer t", req(ClinicSchedule.SLOT_TIMES.get(i)));
        }
        assertConflict(() -> requestService.createRequest(customer, "Bearer t", req(LocalTime.of(14, 0))));

        // Người khác không bị ảnh hưởng.
        assertThat(requestService.createRequest(UUID.randomUUID(), "Bearer t", req(LocalTime.of(14, 0))).status())
                .isEqualTo(AppointmentStatus.AWAITING_ASSIGNMENT);
    }

    @Test
    void cancellingARequest_freesTheSpotAndTheCustomersLimit() {
        UUID customer = UUID.randomUUID();
        AppointmentResponse r = requestService.createRequest(customer, "Bearer t", req(NINE));
        assertConflict(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)));

        // Huỷ lịch chưa có slot không được văng lỗi (không có slot để trả lại).
        AppointmentResponse cancelled = appointmentService.cancelAppointment(r.id(), customer, false);
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);

        assertThat(requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)).status())
                .isEqualTo(AppointmentStatus.AWAITING_ASSIGNMENT);
    }

    @Test
    void anotherCustomer_cannotCancelMyRequest() {
        AppointmentResponse r = requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));
        assertThatThrownBy(() -> appointmentService.cancelAppointment(r.id(), UUID.randomUUID(), false))
                .isInstanceOf(com.vetclinic.booking.exception.ResourceNotFoundException.class);
    }

    @Test
    void unassignedRequest_cannotBeConfirmedOrCompleted_onlyCancelled() {
        AppointmentResponse r = requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));

        for (AppointmentStatus s : List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED,
                AppointmentStatus.NO_SHOW)) {
            assertThatThrownBy(() -> appointmentService.updateStatus(r.id(), s))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }
        assertThat(appointmentService.updateStatus(r.id(), AppointmentStatus.CANCELLED).status())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void search_listsUnassignedRequests_butNotUnderADoctor() {
        UUID customer = UUID.randomUUID();
        requestService.createRequest(customer, "Bearer t", req(NINE));

        // Admin/lễ tân lọc theo ngày thấy lịch chờ xếp…
        assertThat(appointmentService.search(day, AppointmentStatus.AWAITING_ASSIGNMENT, null))
                .extracting(AppointmentResponse::customerUserId).contains(customer);
        // …còn bác sĩ xem lịch của mình thì không (chưa ai phụ trách).
        assertThat(appointmentService.search(day, null, UUID.randomUUID())).isEmpty();
        // Các lịch của khách này đều có mặt ở danh sách của khách.
        assertThat(appointmentService.listMyAppointments(customer)).hasSize(1);
    }

    @Test
    void stats_countUnassignedRequestsAsPendingOnThePreferredDay() {
        requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));

        AppointmentStatsResponse stats = statsService.stats(day, day);
        assertThat(stats.daily()).hasSize(1);
        assertThat(stats.daily().get(0).date()).isEqualTo(day);
        assertThat(stats.daily().get(0).counts().total()).isEqualTo(1);
        assertThat(stats.daily().get(0).counts().pending()).isEqualTo(1);
    }

    @Test
    void clinicHours_showsRemainingSpotsPerTime() {
        doctors(2);
        requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));

        List<ClinicHourResponse> hours = requestService.clinicHours(day);

        assertThat(hours).hasSize(ClinicSchedule.SLOT_TIMES.size());
        ClinicHourResponse nine = hours.stream().filter(h -> h.startTime().equals(NINE)).findFirst().orElseThrow();
        assertThat(nine.remaining()).isEqualTo(1);
        assertThat(nine.available()).isTrue();
        assertThat(nine.endTime()).isEqualTo(NINE.plusMinutes(30));
        ClinicHourResponse ten = hours.stream().filter(h -> h.startTime().equals(LocalTime.of(10, 0)))
                .findFirst().orElseThrow();
        assertThat(ten.remaining()).isEqualTo(2);

        requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE));
        assertThat(requestService.clinicHours(day).stream().filter(h -> h.startTime().equals(NINE))
                .findFirst().orElseThrow().available()).isFalse();
    }

    @Test
    void clinicHours_isEmptyOutsideTheBookingWindow() {
        LocalDate today = LocalDate.now(ClinicSchedule.ZONE_ID);
        assertThat(requestService.clinicHours(today.minusDays(1))).isEmpty();
        assertThat(requestService.clinicHours(today.plusDays(ClinicSchedule.REQUEST_HORIZON_DAYS))).isEmpty();
    }

    @Test
    void clinicHours_markEveryTimeUnavailable_whenNoDoctors() {
        doctors(0);
        assertThat(requestService.clinicHours(day)).allMatch(h -> !h.available() && h.remaining() == 0);
    }

    @Test
    void doctorDirectoryDown_failsClosedInsteadOfOverbooking() {
        when(profileServiceClient.listDoctors()).thenThrow(new RuntimeException("profile-service down"));
        assertThatThrownBy(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t", req(NINE)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    // ---- helpers ----

    private AppointmentRequest req(LocalTime time) {
        return new AppointmentRequest(UUID.randomUUID(), day, time, null, null);
    }

    private void assertBadRequest(LocalDate date, LocalTime time) {
        assertThatThrownBy(() -> requestService.createRequest(UUID.randomUUID(), "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), date, time, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void assertConflict(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private List<DoctorSummaryResponse> doctors(int n) {
        List<DoctorSummaryResponse> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new DoctorSummaryResponse(UUID.randomUUID(), UUID.randomUUID(), "BS " + i, null, null, null));
        }
        when(profileServiceClient.listDoctors()).thenReturn(list);
        return list;
    }

    private PetResponse pet() {
        return new PetResponse(UUID.randomUUID(), UUID.randomUUID(), "Milo", "Dog", "Poodle", "MALE",
                LocalDate.of(2020, 1, 1), null, null, null, Instant.now(), Instant.now());
    }
}
