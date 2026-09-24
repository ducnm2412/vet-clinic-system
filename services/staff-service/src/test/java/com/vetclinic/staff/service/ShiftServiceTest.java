package com.vetclinic.staff.service;

import com.vetclinic.staff.dto.Shifts;
import com.vetclinic.staff.exception.StaffExceptions;
import com.vetclinic.staff.messaging.ShiftChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CN-39, CN-41. Chạy trên staff_db_test (VD-12), rollback sau mỗi test. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class ShiftServiceTest {

    @Autowired private ShiftService shiftService;
    @Autowired private ApplicationEvents events;

    private final UUID doctor = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 5);

    private Shifts.CreateRequest week(LocalDate... dates) {
        return new Shifts.CreateRequest(doctor, List.of(dates), LocalTime.of(8, 0), LocalTime.of(12, 0), "Ca sáng");
    }

    @Test
    void schedulingAWeekCreatesOneShiftPerDayAndTellsBookingService() {
        Shifts.CreateResult result = shiftService.create(week(monday, monday.plusDays(1), monday.plusDays(2)), admin);

        assertThat(result.created()).hasSize(3);
        assertThat(result.skipped()).isEmpty();
        assertThat(events.stream(ShiftChangedEvent.class)).hasSize(3)
                .allSatisfy(event -> {
                    assertThat(event.added()).isTrue();
                    assertThat(event.userId()).isEqualTo(doctor);
                    assertThat(event.startTime()).isEqualTo(LocalTime.of(8, 0));
                });
    }

    @Test
    void schedulingTheSameShiftAgainIsSkippedNotDuplicated() {
        shiftService.create(week(monday), admin);

        Shifts.CreateResult again = shiftService.create(week(monday, monday.plusDays(1)), admin);

        assertThat(again.created()).hasSize(1);
        assertThat(again.skipped()).containsExactly(monday);
        assertThat(shiftService.search(monday, monday, doctor)).hasSize(1);
    }

    @Test
    void removingAShiftTellsBookingServiceToCloseThoseHours() {
        UUID shiftId = shiftService.create(week(monday), admin).created().get(0).id();

        shiftService.delete(shiftId);

        assertThat(shiftService.search(monday, monday, doctor)).isEmpty();
        assertThat(events.stream(ShiftChangedEvent.class)).last()
                .satisfies(event -> assertThat(event.added()).isFalse());
    }

    @Test
    void deletingUnknownShiftIsNotFound() {
        assertThatThrownBy(() -> shiftService.delete(UUID.randomUUID()))
                .isInstanceOf(StaffExceptions.NotFoundException.class);
    }

    @Test
    void refusesShiftEndingBeforeItStarts() {
        Shifts.CreateRequest backwards = new Shifts.CreateRequest(doctor, List.of(monday),
                LocalTime.of(17, 0), LocalTime.of(9, 0), null);

        assertThatThrownBy(() -> shiftService.create(backwards, admin))
                .isInstanceOf(StaffExceptions.InvalidRequestException.class);
        assertThat(events.stream(ShiftChangedEvent.class)).isEmpty();
    }

    @Test
    void searchFiltersByPersonAndRange() {
        UUID otherDoctor = UUID.randomUUID();
        shiftService.create(week(monday, monday.plusDays(1)), admin);
        shiftService.create(new Shifts.CreateRequest(otherDoctor, List.of(monday),
                LocalTime.of(13, 0), LocalTime.of(17, 0), null), admin);

        assertThat(shiftService.search(monday, monday.plusDays(6), null)).hasSize(3);
        assertThat(shiftService.search(monday, monday.plusDays(6), doctor)).hasSize(2);
        assertThat(shiftService.search(monday, monday, doctor)).hasSize(1);
    }
}
