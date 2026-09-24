package com.vetclinic.booking.service;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.DoctorShift;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentRequest;
import com.vetclinic.booking.dto.AppointmentResponse;
import com.vetclinic.booking.dto.PetResponse;
import com.vetclinic.booking.messaging.UserStatusChangedEvent;
import com.vetclinic.booking.messaging.UserStatusChangedListener;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.DoctorShiftRepository;
import com.vetclinic.booking.repository.BlockedDoctorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CN-08: khoá tài khoản bác sĩ thì không nhận thêm lịch. Transaction rollback — nhưng vẫn ghi vào
 * database thật, chỉ chạy với DB_NAME=booking_db_test (xem VD-12).
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class DoctorBlockTest {

    @Autowired private UserStatusChangedListener listener;
    @Autowired private SlotService slotService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentSlotRepository slotRepository;
    @Autowired private BlockedDoctorRepository blockedDoctorRepository;
    @Autowired private DoctorShiftRepository doctorShiftRepository;
    @MockBean private ProfileServiceClient profileServiceClient;

    private final UUID doctor = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    /** CN-39: bác sĩ phải có ca thì mở khoá mới sinh bù được khung giờ. */
    private void givenFullDayShift(LocalDate date) {
        doctorShiftRepository.saveAndFlush(DoctorShift.builder().doctorUserId(doctor).date(date)
                .startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59)).build());
    }

    private AppointmentSlot slot(LocalDate date, SlotStatus status) {
        LocalTime time = LocalTime.of(10, 0);
        return slotRepository.saveAndFlush(AppointmentSlot.builder().doctorUserId(doctor).date(date)
                .startTime(time).endTime(time.plusMinutes(30)).status(status).build());
    }

    private SlotStatus statusOf(AppointmentSlot s) {
        return slotRepository.findById(s.getId()).orElseThrow().getStatus();
    }

    @Test
    void lockBlocksFutureFreeSlotsOnlyAndUnlockReopensThem() {
        AppointmentSlot yesterday = slot(today.minusDays(1), SlotStatus.AVAILABLE);
        AppointmentSlot tomorrowFree = slot(today.plusDays(1), SlotStatus.AVAILABLE);
        AppointmentSlot tomorrowBooked = slot(today.plusDays(2), SlotStatus.BOOKED);

        listener.onUserStatusChanged(new UserStatusChangedEvent(doctor, List.of("DOCTOR"), true));

        assertThat(statusOf(tomorrowFree)).isEqualTo(SlotStatus.BLOCKED);
        assertThat(statusOf(tomorrowBooked)).as("lịch đã đặt giữ nguyên").isEqualTo(SlotStatus.BOOKED);
        assertThat(statusOf(yesterday)).as("quá khứ không đụng tới").isEqualTo(SlotStatus.AVAILABLE);
        givenFullDayShift(today.plusDays(3));
        assertThat(slotService.generateSlots(doctor, today.plusDays(3))).as("không sinh slot mới").isEmpty();

        // Nhận trùng sự kiện không lỗi.
        listener.onUserStatusChanged(new UserStatusChangedEvent(doctor, List.of("DOCTOR"), true));

        listener.onUserStatusChanged(new UserStatusChangedEvent(doctor, List.of("DOCTOR"), false));

        assertThat(statusOf(tomorrowFree)).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(blockedDoctorRepository.existsById(doctor)).isFalse();
        assertThat(slotRepository.findByDoctorUserIdAndDate(doctor, today.plusDays(3)))
                .as("sinh bù giờ khám cho ngày bị bỏ qua lúc khoá").isNotEmpty();
    }

    @Test
    void cancellingAppointmentOfLockedDoctorDoesNotReopenTheSlot() {
        when(profileServiceClient.getMyPet(any(), any())).thenReturn(new PetResponse(UUID.randomUUID(), "Milo", "Chó", null, null,
                null, null, Instant.now(), Instant.now()));
        // Ngày rất xa, ngoài tầm sinh slot tự động — không bác sĩ nào khác trống cùng giờ.
        LocalDate date = today.plusDays(400);
        AppointmentSlot s = slot(date, SlotStatus.AVAILABLE);
        UUID customer = UUID.randomUUID();
        AppointmentResponse booked = appointmentService.createAppointment(customer, "Bearer t",
                new AppointmentRequest(UUID.randomUUID(), date, s.getStartTime(), "Khám"));
        assertThat(booked.slotId()).isEqualTo(s.getId());

        listener.onUserStatusChanged(new UserStatusChangedEvent(doctor, List.of("DOCTOR"), true));
        appointmentService.cancelAppointment(booked.id(), customer, false);

        assertThat(statusOf(s)).isEqualTo(SlotStatus.BLOCKED);
    }

    @Test
    void lockingNonDoctorDoesNothing() {
        AppointmentSlot free = slot(today.plusDays(1), SlotStatus.AVAILABLE);

        listener.onUserStatusChanged(new UserStatusChangedEvent(doctor, List.of("STAFF"), true));

        assertThat(statusOf(free)).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(blockedDoctorRepository.existsById(doctor)).isFalse();
    }
}
