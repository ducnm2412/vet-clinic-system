package com.vetclinic.reporting.dto;

import java.time.LocalDate;
import java.util.Locale;

/** Độ chi tiết của biểu đồ: theo ngày, tháng hoặc quý (CN-46). */
public enum Interval {
    DAY,
    MONTH,
    QUARTER;

    /** Ngày đầu của kỳ chứa {@code date}. */
    public LocalDate periodStart(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> date.withMonth((date.getMonthValue() - 1) / 3 * 3 + 1).withDayOfMonth(1);
        };
    }

    public LocalDate nextPeriodStart(LocalDate periodStart) {
        return switch (this) {
            case DAY -> periodStart.plusDays(1);
            case MONTH -> periodStart.plusMonths(1);
            case QUARTER -> periodStart.plusMonths(3);
        };
    }

    /** Nhận "day", "month", "quarter" không phân biệt hoa thường. */
    public static Interval parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidReportRangeException("interval phải là day, month hoặc quarter");
        }
    }
}
