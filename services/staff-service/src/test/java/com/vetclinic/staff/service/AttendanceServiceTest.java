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

    // ---------- đi muộn / về sớm ----------

    @Test
    void lateBeyondGraceReportsTheFullMinutes() {
        LocalDate date = today.minusDays(1);
        shift(me, date, 8, 12);
        punch(me, date, LocalTime.of(8, 25), LocalTime.of(12, 0));

        Attendance.Record r = only(me, date);

        assertThat(r.lateMinutes()).isEqualTo(25);
        assertThat(r.earlyLeaveMinutes()).isZero();
    }

    @Test
    void withinGraceCountsAsOnTimeAndOneMinuteOverDoesNot() {
        LocalDate date = today.minusDays(1);
        UUID other = UUID.randomUUID();
        shift(me, date, 8, 12);
        shift(other, date, 8, 12);
        punch(me, date, LocalTime.of(8, 10), LocalTime.of(11, 50));
        punch(other, date, LocalTime.of(8, 11), LocalTime.of(11, 49));

        // Đúng 10 phút (cả vào lẫn ra) vẫn đúng giờ; thêm 1 phút thì ghi toàn bộ độ lệch.
        assertThat(only(me, date).lateMinutes()).isZero();
        assertThat(only(me, date).earlyLeaveMinutes()).isZero();
        assertThat(only(other, date).lateMinutes()).isEqualTo(11);
        assertThat(only(other, date).earlyLeaveMinutes()).isEqualTo(11);
    }

    @Test
    void arrivingEarlyIsNotLate() {
        LocalDate date = today.minusDays(1);
        shift(me, date, 8, 12);
        punch(me, date, LocalTime.of(7, 30), LocalTime.of(12, 30));

        assertThat(only(me, date).lateMinutes()).isZero();
        assertThat(only(me, date).earlyLeaveMinutes()).isZero();
    }

    @Test
    void leavingEarlyIsReported() {
        LocalDate date = today.minusDays(1);
        shift(me, date, 8, 12);
        punch(me, date, LocalTime.of(8, 0), LocalTime.of(11, 30));

        assertThat(only(me, date).earlyLeaveMinutes()).isEqualTo(30);
    }

    @Test
    void lunchBreakBetweenTwoShiftsIsNotLeavingEarly() {
        LocalDate date = today.minusDays(1);
        shift(me, date, 8, 12);
        shift(me, date, 13, 17);
        punch(me, date, LocalTime.of(8, 0), LocalTime.of(17, 0));

        Attendance.Record r = only(me, date);

        // Ca được báo là từ đầu ca đầu đến cuối ca cuối, và không có gì để ghi muộn/sớm.
        assertThat(r.shiftStart()).isEqualTo(LocalTime.of(8, 0));
        assertThat(r.shiftEnd()).isEqualTo(LocalTime.of(17, 0));
        assertThat(r.lateMinutes()).isZero();
        assertThat(r.earlyLeaveMinutes()).isZero();
    }

    @Test
    void workingOutsideAnyShiftHasNothingToCompareAgainst() {
        LocalDate date = today.minusDays(1);
        punch(me, date, LocalTime.of(15, 0), LocalTime.of(16, 0));

        Attendance.Record r = only(me, date);

        assertThat(r.shiftStart()).isNull();
        assertThat(r.lateMinutes()).isZero();
        assertThat(r.earlyLeaveMinutes()).isZero();
    }

    @Test
    void stillInShiftIsNeverEarlyLeave() {
        LocalDate date = today.minusDays(1);
        shift(me, date, 8, 12);
        attendanceRepository.saveAndFlush(AttendanceRecord.builder().userId(me).date(date)
                .checkInAt(date.atTime(8, 0).atZone(AttendanceService.CLINIC_ZONE).toInstant()).build());

        assertThat(only(me, date).earlyLeaveMinutes()).isZero();
    }

    // ---------- vắng mặt ----------

    @Test
    void absenceIsAPastShiftWithNoAttendanceRecord() {
        UUID other = UUID.randomUUID();
        shift(me, today.minusDays(2), 8, 12);          // vắng
        shift(me, today.minusDays(1), 8, 12);          // có đi làm
        punch(me, today.minusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 0));
        shift(me, today, 8, 12);                       // hôm nay: chưa khép sổ
        shift(me, today.plusDays(1), 8, 12);           // tương lai
        shift(other, today.minusDays(3), 13, 17);      // người khác cũng vắng

        var all = attendanceService.absences(today.minusDays(5), today.plusDays(2), null);
        var mine = attendanceService.absences(today.minusDays(5), today.plusDays(2), me);

        assertThat(all).extracting(Attendance.Absence::userId, Attendance.Absence::date)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(me, today.minusDays(2)),
                        org.assertj.core.groups.Tuple.tuple(other, today.minusDays(3)));
        assertThat(mine).singleElement().satisfies(a -> {
            assertThat(a.date()).isEqualTo(today.minusDays(2));
            assertThat(a.shiftStart()).isEqualTo(LocalTime.of(8, 0));
            assertThat(a.shiftEnd()).isEqualTo(LocalTime.of(12, 0));
        });
    }

    @Test
    void twoShiftsInOneDayAreOneAbsence() {
        shift(me, today.minusDays(1), 8, 12);
        shift(me, today.minusDays(1), 13, 17);

        assertThat(attendanceService.absences(today.minusDays(2), today, me)).singleElement()
                .satisfies(a -> {
                    assertThat(a.shiftStart()).isEqualTo(LocalTime.of(8, 0));
                    assertThat(a.shiftEnd()).isEqualTo(LocalTime.of(17, 0));
                });
    }

    @Test
    void timesheetCountsLateEarlyAndAbsentDays() {
        shift(me, today.minusDays(4), 8, 12);
        punch(me, today.minusDays(4), LocalTime.of(8, 30), LocalTime.of(12, 0));   // muộn
        shift(me, today.minusDays(3), 8, 12);
        punch(me, today.minusDays(3), LocalTime.of(8, 0), LocalTime.of(11, 0));    // về sớm
        shift(me, today.minusDays(2), 8, 12);
        punch(me, today.minusDays(2), LocalTime.of(8, 30), LocalTime.of(11, 0));   // cả hai, vẫn là hai ngày riêng
        shift(me, today.minusDays(1), 8, 12);                                      // vắng

        Attendance.TimesheetRow row = attendanceService.timesheet(today.minusDays(5), today, me).rows().get(0);

        assertThat(row.daysWorked()).isEqualTo(3);
        assertThat(row.shiftsAssigned()).isEqualTo(4);
        assertThat(row.daysLate()).isEqualTo(2);
        assertThat(row.daysEarlyLeave()).isEqualTo(2);
        assertThat(row.daysAbsent()).isEqualTo(1);
    }

    @Test
    void absencesRejectReversedRange() {
        assertThatThrownBy(() -> attendanceService.absences(today, today.minusDays(1), null))
                .isInstanceOf(StaffExceptions.InvalidRequestException.class);
    }

    private Attendance.Record only(UUID userId, LocalDate date) {
        return attendanceService.search(date, date, userId).stream().findFirst().orElseThrow();
    }

    private void shift(UUID userId, LocalDate date, int fromHour, int toHour) {
        shiftRepository.saveAndFlush(Shift.builder().userId(userId).date(date)
                .startTime(LocalTime.of(fromHour, 0)).endTime(LocalTime.of(toHour, 0)).build());
    }

    private void punch(UUID userId, LocalDate date, LocalTime in, LocalTime out) {
        attendanceRepository.saveAndFlush(AttendanceRecord.builder().userId(userId).date(date)
                .checkInAt(date.atTime(in).atZone(AttendanceService.CLINIC_ZONE).toInstant())
                .checkOutAt(date.atTime(out).atZone(AttendanceService.CLINIC_ZONE).toInstant())
                .build());
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
