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
     * {@code shiftStart}/{@code shiftEnd} là ca được xếp hôm đó (null nếu đi làm ngoài ca),
     * để nhìn ra ai đi muộn, ai về sớm.
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
            long shiftsAssigned
    ) {
    }

    public record Timesheet(LocalDate from, LocalDate to, List<TimesheetRow> rows) {
    }
}
