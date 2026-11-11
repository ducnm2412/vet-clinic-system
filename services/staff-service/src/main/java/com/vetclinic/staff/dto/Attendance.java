package com.vetclinic.staff.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** CN-38, CN-40: chấm công và giờ công. */
public final class Attendance {

    private Attendance() {
    }

    public record Request(@Size(max = 500) String note) {
    }

    /**
     * Một ngày công. {@code workedMinutes} chưa tính khi còn đang trong ca.
     * {@code shiftStart}/{@code shiftEnd} là ca được xếp hôm đó (null nếu đi làm ngoài ca): giờ bắt
     * đầu của ca đầu tiên và giờ kết thúc của ca cuối cùng trong ngày.
     *
     * {@code lateMinutes}/{@code earlyLeaveMinutes} là số phút vào muộn / ra sớm so với ca, chỉ tính khi
     * vượt dung sai (xem AttendanceService.GRACE_MINUTES); 0 nghĩa là đúng giờ, hoặc đi ngoài ca
     * (không có ca thì không có gì để so), hoặc chưa ra ca (chưa biết có về sớm không).
     */
    public record Record(
            UUID id,
            UUID userId,
            LocalDate date,
            Instant checkInAt,
            Instant checkOutAt,
            long workedMinutes,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            long lateMinutes,
            long earlyLeaveMinutes,
            String note
    ) {
    }

    /** CN-40, CN-48: tổng hợp theo người trong một kỳ. */
    public record TimesheetRow(
            UUID userId,
            /** Số ngày có chấm công (kể cả ngày quên ra ca). */
            long daysWorked,
            long totalMinutes,
            /** Ngày đã vào ca nhưng chưa ra ca — giờ công của những ngày đó chưa tính được. */
            long daysMissingCheckOut,
            long shiftsAssigned,
            /** Số ngày vào ca muộn quá dung sai. */
            long daysLate,
            /** Số ngày ra ca sớm quá dung sai. */
            long daysEarlyLeave,
            /** Số ngày có ca được xếp nhưng không chấm công (chỉ tính các ngày đã qua). */
            long daysAbsent
    ) {
    }

    /** Một ngày có ca được xếp mà không có bản ghi chấm công nào. */
    public record Absence(
            UUID userId,
            LocalDate date,
            LocalTime shiftStart,
            LocalTime shiftEnd
    ) {
    }

    public record Timesheet(LocalDate from, LocalDate to, List<TimesheetRow> rows) {
    }
}
