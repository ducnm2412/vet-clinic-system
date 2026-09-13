package com.vetclinic.reporting.dto;

import com.vetclinic.reporting.client.SourceClients.AppointmentCounts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Hình dạng dữ liệu reporting-service trả cho frontend.
 *
 * Quy ước khi một nguồn không trả lời: field của nguồn đó là null (KHÔNG phải 0) và tên nguồn
 * nằm trong {@code unavailable}. Số 0 nghĩa là "đã hỏi, thật sự không có" — hai chuyện khác nhau,
 * admin không được nhìn nhầm "chưa lấy được doanh thu" thành "doanh thu bằng 0".
 * Tên nguồn: {@code sales} (order-service), {@code services} (payment-service),
 * {@code appointments} (booking-service).
 */
public final class Reports {

    private Reports() {
    }

    public record Revenue(LocalDate from, LocalDate to, Interval interval, List<RevenuePoint> points,
                          RevenueTotals totals, List<String> unavailable) {
    }

    /** Doanh thu một kỳ. {@code periodEnd} bị cắt theo khoảng đang xem, nên kỳ đầu/cuối có thể ngắn hơn. */
    public record RevenuePoint(LocalDate periodStart, LocalDate periodEnd, BigDecimal sales, BigDecimal services,
                               BigDecimal total) {
    }

    public record RevenueTotals(BigDecimal sales, BigDecimal services, BigDecimal total, Long ordersCompleted,
                                Long payments) {
    }

    public record Appointments(LocalDate from, LocalDate to, Interval interval, AppointmentCounts totals,
                               Double cancellationRate, Double noShowRate, List<AppointmentPoint> points,
                               List<DoctorRow> byDoctor) {
    }

    public record AppointmentPoint(LocalDate periodStart, LocalDate periodEnd, AppointmentCounts counts) {
    }

    /** {@code doctorName} null khi hồ sơ chưa có tên hoặc profile-service không trả lời. */
    public record DoctorRow(UUID doctorUserId, String doctorName, AppointmentCounts counts,
                            Double cancellationRate) {
    }

    public record Summary(LocalDate from, LocalDate to, BigDecimal salesRevenue, BigDecimal serviceRevenue,
                          BigDecimal totalRevenue, OrderTotals orders, AppointmentCounts appointments,
                          Double cancellationRate, List<String> unavailable) {
    }

    public record OrderTotals(long created, long completed, long cancelled) {
    }
}
