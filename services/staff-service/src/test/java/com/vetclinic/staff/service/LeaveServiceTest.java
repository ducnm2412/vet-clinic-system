package com.vetclinic.staff.service;

import com.vetclinic.staff.dto.Leaves;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Admin ghi tay ngày nghỉ. Chạy trên staff_db_test, rollback sau mỗi test. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class LeaveServiceTest {

    @Autowired private LeaveService leaveService;
    @Autowired private ShiftService shiftService;
    @Autowired private ApplicationEvents events;

    private final UUID doctor = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final LocalDate monday = LocalDate.of(2026, 10, 5);

    private Leaves.CreateRequest leave(String reason, LocalDate... dates) {
        return new Leaves.CreateRequest(doctor, List.of(dates), reason);
    }

    @Test
    void recordingALeaveSavesEachDayWithItsReason() {
        Leaves.CreateResult result = leaveService.create(leave("  Ốm  ", monday, monday.plusDays(1)), admin);

        assertThat(result.created()).hasSize(2);
        assertThat(result.removedShifts()).isZero();
        assertThat(leaveService.search(monday, monday.plusDays(6), doctor))
                .extracting(Leaves.Response::reason).containsOnly("Ốm");
    }

    @Test
    void recordingTheSameDayAgainIsSkippedNotDuplicated() {
        leaveService.create(leave(null, monday), admin);

        Leaves.CreateResult again = leaveService.create(leave(null, monday, monday.plusDays(1)), admin);

        assertThat(again.created()).hasSize(1);
        assertThat(again.skipped()).containsExactly(monday);
    }

    @Test
    void aLeaveRemovesTheShiftOfThatDayAndTellsBookingToCloseTheHours() {
        shiftService.create(new Shifts.CreateRequest(doctor, List.of(monday, monday.plusDays(1)), null, null, null),
                admin);

        Leaves.CreateResult result = leaveService.create(leave("Việc gia đình", monday), admin);

        assertThat(result.removedShifts()).isEqualTo(1);
        // Chỉ ngày nghỉ bị gỡ, ngày còn lại vẫn có ca.
        assertThat(shiftService.search(monday, monday.plusDays(1), doctor))
                .extracting(Shifts.Response::date).containsExactly(monday.plusDays(1));
        assertThat(events.stream(ShiftChangedEvent.class)).last().satisfies(event -> {
            assertThat(event.added()).isFalse();
            assertThat(event.userId()).isEqualTo(doctor);
            assertThat(event.date()).isEqualTo(monday);
        });
    }

    @Test
    void cannotScheduleAShiftOnADayOffUntilTheLeaveIsRemoved() {
        UUID leaveId = leaveService.create(leave(null, monday), admin).created().get(0).id();
        Shifts.CreateRequest request = new Shifts.CreateRequest(doctor, List.of(monday), null, null, null);

        assertThatThrownBy(() -> shiftService.create(request, admin))
                .isInstanceOf(StaffExceptions.InvalidRequestException.class)
                .hasMessageContaining("05/10/2026");

        leaveService.delete(leaveId);
        assertThat(shiftService.create(request, admin).created()).hasSize(1);
    }

    @Test
    void someoneElseOnLeaveDoesNotBlockThisPerson() {
        leaveService.create(new Leaves.CreateRequest(UUID.randomUUID(), List.of(monday), null), admin);

        assertThat(shiftService.create(new Shifts.CreateRequest(doctor, List.of(monday), null, null, null), admin)
                .created()).hasSize(1);
    }

    @Test
    void deletingUnknownLeaveIsNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> leaveService.delete(unknown)).isInstanceOf(StaffExceptions.NotFoundException.class);
    }
}
