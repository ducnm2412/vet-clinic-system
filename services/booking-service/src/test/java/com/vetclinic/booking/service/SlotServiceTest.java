package com.vetclinic.booking.service;

import com.vetclinic.booking.domain.AppointmentSlot;
import com.vetclinic.booking.domain.ClinicSchedule;
import com.vetclinic.booking.domain.SlotStatus;
import com.vetclinic.booking.dto.AppointmentSlotResponse;
import com.vetclinic.booking.dto.AvailableTimeResponse;
import com.vetclinic.booking.repository.AppointmentSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class SlotServiceTest {

    @Autowired
    private SlotService slotService;

    @Autowired
    private AppointmentSlotRepository appointmentSlotRepository;

    @Test
    void generateSlots_createsOneSlotPerScheduledTime() {
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 1);

        List<AppointmentSlotResponse> created = slotService.generateSlots(doctorId, date);

        assertThat(created).hasSize(ClinicSchedule.SLOT_TIMES.size());
        assertThat(created).allMatch(s -> s.status() == SlotStatus.AVAILABLE);
        assertThat(created).extracting(AppointmentSlotResponse::startTime)
                .containsExactlyInAnyOrderElementsOf(ClinicSchedule.SLOT_TIMES);
    }

    @Test
    void generateSlots_isIdempotent_secondCallCreatesNothing() {
        UUID doctorId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 2);

        List<AppointmentSlotResponse> firstCall = slotService.generateSlots(doctorId, date);
        List<AppointmentSlotResponse> secondCall = slotService.generateSlots(doctorId, date);

        assertThat(firstCall).hasSize(ClinicSchedule.SLOT_TIMES.size());
        assertThat(secondCall).isEmpty();
        assertThat(appointmentSlotRepository.findByDoctorUserIdAndDate(doctorId, date))
                .hasSize(ClinicSchedule.SLOT_TIMES.size());
    }

    @Test
    void getAvailableTimes_countsAcrossDoctors_andExcludesBookedSlots() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        UUID doctorA = UUID.randomUUID();
        UUID doctorB = UUID.randomUUID();

        slotService.generateSlots(doctorA, date);
        slotService.generateSlots(doctorB, date);

        List<AppointmentSlot> doctorBSlots = appointmentSlotRepository.findByDoctorUserIdAndDate(doctorB, date);
        AppointmentSlot toBook = doctorBSlots.stream()
                .filter(s -> s.getStartTime().equals(LocalTime.of(8, 0)))
                .findFirst().orElseThrow();
        toBook.setStatus(SlotStatus.BOOKED);
        appointmentSlotRepository.saveAndFlush(toBook);

        List<AvailableTimeResponse> availableTimes = slotService.getAvailableTimes(date);

        // 2 bác sĩ x 16 giờ, chỉ 1 slot của doctorB lúc 8h bị BOOKED nên vẫn còn đủ 16 khung giờ,
        // riêng khung 8h chỉ còn 1 bác sĩ (doctorA) thay vì 2.
        assertThat(availableTimes).hasSize(ClinicSchedule.SLOT_TIMES.size());

        AvailableTimeResponse nineAm = availableTimes.stream()
                .filter(t -> t.startTime().equals(LocalTime.of(9, 0)))
                .findFirst().orElseThrow();
        assertThat(nineAm.availableCount()).isEqualTo(2);
        assertThat(nineAm.endTime()).isEqualTo(LocalTime.of(9, 30));

        AvailableTimeResponse eightAm = availableTimes.stream()
                .filter(t -> t.startTime().equals(LocalTime.of(8, 0)))
                .findFirst().orElseThrow();
        assertThat(eightAm.availableCount()).isEqualTo(1);
    }
}
