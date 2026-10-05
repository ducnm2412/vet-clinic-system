package com.vetclinic.staff.service;

import com.vetclinic.staff.domain.AttendanceRecord;
import com.vetclinic.staff.domain.Shift;
import com.vetclinic.staff.dto.Attendance;
import com.vetclinic.staff.exception.StaffExceptions;
import com.vetclinic.staff.repository.AttendanceRecordRepository;
import com.vetclinic.staff.repository.ShiftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CN-38, CN-40. Chạy trên staff_db_test (VD-12), rollback sau mỗi test. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class AttendanceServiceTest {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRecordRepository attendanceRepository;
    @Autowired private ShiftRepository shiftRepository;

    private final UUID me = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(AttendanceService.CLINIC_ZONE);

    @Test
    void checkInThenCheckOutCountsWorkedTime() {
        attendanceService.checkIn(me, "Vào ca sáng");

        // Lùi giờ vào ca 3 tiếng để có khoảng thời gian thật mà tính, không phải chờ.
        AttendanceRecord saved = attendanceRepository.findByUserIdAndDate(me, today).orElseThrow();
        saved.setCheckInAt(Instant.now().minus(3, ChronoUnit.HOURS));
        attendanceRepository.saveAndFlush(saved);

        Attendance.Record result = attendanceService.checkOut(me, null);

        assertThat(result.checkOutAt()).isNotNull();
        assertThat(result.workedMinutes()).isBetween(179L, 181L);
        assertThat(result.note()).isEqualTo("Vào ca sáng");
    }

    @Test
    void cannotCheckInTwiceInOneDay() {
        attendanceService.checkIn(me, null);

        assertThatThrownBy(() -> attendanceService.checkIn(me, null))
                .isInstanceOf(StaffExceptions.InvalidStateException.class)
                .hasMessageContaining("đã vào ca");
    }

    @Test
    void cannotCheckOutWithoutCheckingIn() {
        assertThatThrownBy(() -> attendanceService.checkOut(me, null))
                .isInstanceOf(StaffExceptions.InvalidStateException.class)
                .hasMessageContaining("chưa vào ca");
    }

    @Test
    void cannotCheckOutTwice() {
        attendanceService.checkIn(me, null);
        attendanceService.checkOut(me, null);

        assertThatThrownBy(() -> attendanceService.checkOut(me, null))
                .isInstanceOf(StaffExceptions.InvalidStateException.class)
                .hasMessageContaining("đã ra ca");
    }

    @Test
    void todayShowsShiftAssignedSoLateArrivalIsVisible() {
        shiftRepository.saveAndFlush(Shift.builder().userId(me).date(today)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0)).build());
        attendanceService.checkIn(me, null);

        Attendance.Record record = attendanceService.today(me);

        assertThat(record.shiftStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(record.shiftEnd()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void todayIsNullBeforeFirstCheckIn() {
        assertThat(attendanceService.today(me)).isNull();
    }

    @Test
    void timesheetSumsMinutesAndFlagsDaysWithoutCheckOut() {
        UUID other = UUID.randomUUID();
        LocalDate from = today.minusDays(3);
        // Hai ngày đủ đôi vào–ra, một ngày quên ra ca.
        record(me, today.minusDays(3), 8, 12);
        record(me, today.minusDays(2), 13, 17);
        openRecord(me, today.minusDays(1));
        record(other, today.minusDays(1), 8, 10);
        shiftRepository.saveAndFlush(Shift.builder().userId(me).date(today.minusDays(3))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0)).build());

        Attendance.Timesheet sheet = attendanceService.timesheet(from, today, null);

        Attendance.TimesheetRow mine = sheet.rows().stream().filter(r -> r.userId().equals(me)).findFirst().orElseThrow();
        assertThat(mine.daysWorked()).isEqualTo(3);
        assertThat(mine.totalMinutes()).isEqualTo(8 * 60);
        assertThat(mine.daysMissingCheckOut()).isEqualTo(1);
        assertThat(mine.shiftsAssigned()).isEqualTo(1);
        // Người làm nhiều giờ hơn đứng trước.
        assertThat(sheet.rows().get(0).userId()).isEqualTo(me);
        assertThat(sheet.rows()).anySatisfy(row -> assertThat(row.userId()).isEqualTo(other));
    }

    @Test
    void timesheetForOnePersonIgnoresOthers() {
        UUID other = UUID.randomUUID();
        record(me, today.minusDays(1), 8, 12);
        record(other, today.minusDays(1), 8, 12);

        Attendance.Timesheet sheet = attendanceService.timesheet(today.minusDays(1), today, me);

        assertThat(sheet.rows()).singleElement().satisfies(row -> assertThat(row.userId()).isEqualTo(me));
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> attendanceService.timesheet(today, today.minusDays(1), null))
                .isInstanceOf(StaffExceptions.InvalidRequestException.class);
    }

    private void record(UUID userId, LocalDate date, int fromHour, int toHour) {
        attendanceRepository.saveAndFlush(AttendanceRecord.builder()
                .userId(userId).date(date)
                .checkInAt(date.atTime(fromHour, 0).atZone(AttendanceService.CLINIC_ZONE).toInstant())
                .checkOutAt(date.atTime(toHour, 0).atZone(AttendanceService.CLINIC_ZONE).toInstant())
                .build());
    }

    private void openRecord(UUID userId, LocalDate date) {
        attendanceRepository.saveAndFlush(AttendanceRecord.builder()
                .userId(userId).date(date)
                .checkInAt(date.atTime(8, 0).atZone(AttendanceService.CLINIC_ZONE).toInstant())
                .build());
    }
}
