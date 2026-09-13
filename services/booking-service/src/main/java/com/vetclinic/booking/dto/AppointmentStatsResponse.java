package com.vetclinic.booking.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CN-47: số lịch khám theo trạng thái, gộp theo ngày khám và theo bác sĩ, cho reporting-service.
 * Ngày không có lịch nào thì không có dòng — reporting-service tự điền số 0.
 */
public record AppointmentStatsResponse(List<Daily> daily, List<ByDoctor> byDoctor) {

    public record Counts(long total, long pending, long confirmed, long completed, long cancelled, long noShow) {
    }

    public record Daily(LocalDate date, Counts counts) {
    }

    /** Chỉ có userId — tên bác sĩ nằm ở profile-service, reporting-service tự tra. */
    public record ByDoctor(UUID doctorUserId, Counts counts) {
    }
}
