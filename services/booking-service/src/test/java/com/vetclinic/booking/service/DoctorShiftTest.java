package com.vetclinic.booking.service;

import com.vetclinic.booking.client.ProfileServiceClient;
import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.messaging.ShiftChangedEvent;
import com.vetclinic.booking.messaging.ShiftChangedListener;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import com.vetclinic.booking.repository.DoctorShiftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CN-39 / VD-11: ca trực quyết định giờ khám. Chạy trên booking_db_test (VD-12), rollback sau mỗi
 * test.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class DoctorShiftTest {

    @MockBean private ProfileServiceClient profileServiceClient;

    @Autowired private ShiftChangedListener listener;
    @Autowired private SlotService slotService;
    @Autowired private AppointmentSlotRepository slotRepository;
    @Autowired private DoctorShiftRepository doctorShiftRepository;

    private final UUID doctor = UUID.randomUUID();
    private final LocalDate workday = LocalDate.now().plusDays(3);

    private List<LocalTime> slotTimes() {
        return slotRepository.findByDoctorUserIdAndDate(doctor, workday).stream()
                .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
                .map(AppointmentSlot::getStartTime)
                .sorted()
                .toList();
    }

    @Test
    void morningShiftOpensMorningHoursOnly() {
        listener.onShiftChanged(new ShiftChangedEvent(doctor, workday,
                LocalTime.of(8, 0), LocalTime.of(12, 0), true));

        assertThat(slotTimes()).isNotEmpty()
                .allSatisfy(time -> assertThat(time).isBetween(LocalTime.of(8, 0), LocalTime.of(11, 30)));
        // Giờ chiều nằm ngoài ca sáng nên không mở.
        assertThat(slotTimes()).doesNotContain(LocalTime.of(13, 0), LocalTime.of(16, 30));
    }

    @Test
    void doctorWithoutShiftGetsNoHoursAtAll() {
        assertThat(slotService.generateSlots(doctor, workday)).isEmpty();
        assertThat(slotTimes()).isEmpty();
    }

    @Test
    void removingShiftClosesFreeHoursButKeepsBookedOnes() {
        listener.onShiftChanged(new ShiftChangedEvent(doctor, workday,
                LocalTime.of(8, 0), LocalTime.of(12, 0), true));
        AppointmentSlot booked = slotRepository.findByDoctorUserIdAndDate(doctor, workday).stream()
                .filter(s -> s.getStartTime().equals(LocalTime.of(9, 0))).findFirst().orElseThrow();
        booked.setStatus(SlotStatus.BOOKED);
        slotRepository.saveAndFlush(booked);

        listener.onShiftChanged(new ShiftChangedEvent(doctor, workday,
                LocalTime.of(8, 0), LocalTime.of(12, 0), false));

        assertThat(slotTimes()).as("giờ còn trống bị đóng").isEmpty();
        assertThat(slotRepository.findById(booked.getId()).orElseThrow().getStatus())
                .as("lịch khách đã đặt giữ nguyên, phải do người quyết định huỷ")
                .isEqualTo(SlotStatus.BOOKED);
        assertThat(doctorShiftRepository.findByDoctorUserIdAndDate(doctor, workday)).isEmpty();
    }

    @Test
    void twoShiftsInADayOpenBothWindows() {
        listener.onShiftChanged(new ShiftChangedEvent(doctor, workday,
                LocalTime.of(8, 0), LocalTime.of(10, 0), true));
        listener.onShiftChanged(new ShiftChangedEvent(doctor, workday,
                LocalTime.of(15, 0), LocalTime.of(17, 0), true));

        assertThat(slotTimes()).contains(LocalTime.of(8, 0), LocalTime.of(9, 30),
                LocalTime.of(15, 0), LocalTime.of(16, 30));
        assertThat(slotTimes()).doesNotContain(LocalTime.of(11, 0), LocalTime.of(13, 0));
    }

    @Test
    void sameShiftEventTwiceChangesNothing() {
        ShiftChangedEvent event = new ShiftChangedEvent(doctor, workday,
                LocalTime.of(8, 0), LocalTime.of(12, 0), true);
        listener.onShiftChanged(event);
        int afterFirst = slotTimes().size();

        listener.onShiftChanged(event);

        assertThat(slotTimes()).hasSize(afterFirst);
        assertThat(doctorShiftRepository.findByDoctorUserIdAndDate(doctor, workday)).hasSize(1);
    }
}
