package com.vetclinic.reporting.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Nguồn số liệu của báo cáo. reporting-service không có database: mỗi lần xem báo cáo, nó hỏi
 * từng service đang sở hữu dữ liệu rồi ghép lại.
 *
 * Token của admin được chuyển nguyên sang — service nguồn tự kiểm tra lại quyền ADMIN, nên
 * gọi thẳng service nguồn cũng không đọc được gì nhiều hơn đi qua đây.
 *
 * Ngày truyền dạng chuỗi ISO (yyyy-MM-dd): để Feign tự đổi LocalDate thì nó định dạng theo
 * locale ("9/14/25") và service nguồn trả 400.
 *
 * Record bên dưới là bản sao riêng của reporting-service; chỉ cần khớp tên field JSON.
 */
public final class SourceClients {

    private SourceClients() {
    }

    @FeignClient(name = "order-service")
    public interface OrderStatsClient {
        @GetMapping("/orders/stats")
        List<OrderDaily> daily(@RequestParam("from") String from, @RequestParam("to") String to,
                               @RequestHeader("Authorization") String bearerToken);
    }

    @FeignClient(name = "payment-service")
    public interface PaymentStatsClient {
        @GetMapping("/payment/stats")
        List<PaymentDaily> daily(@RequestParam("from") String from, @RequestParam("to") String to,
                                 @RequestHeader("Authorization") String bearerToken);
    }

    @FeignClient(name = "booking-service")
    public interface BookingStatsClient {
        @GetMapping("/booking/stats")
        AppointmentStats stats(@RequestParam("from") String from, @RequestParam("to") String to,
                               @RequestHeader("Authorization") String bearerToken);
    }

    @FeignClient(name = "profile-service")
    public interface ProfileClient {
        /** Công khai — chỉ để lấy họ tên bác sĩ. */
        @GetMapping("/profile/doctors")
        List<Doctor> doctors();
    }

    public record OrderDaily(LocalDate date, long ordersCreated, long ordersCompleted, long ordersCancelled,
                             BigDecimal revenue) {
    }

    public record PaymentDaily(LocalDate date, long payments, BigDecimal revenue) {
    }

    public record AppointmentStats(List<AppointmentDaily> daily, List<AppointmentByDoctor> byDoctor) {
    }

    public record AppointmentDaily(LocalDate date, AppointmentCounts counts) {
    }

    public record AppointmentByDoctor(UUID doctorUserId, AppointmentCounts counts) {
    }

    public record AppointmentCounts(long total, long pending, long confirmed, long completed, long cancelled,
                                    long noShow) {

        public static final AppointmentCounts ZERO = new AppointmentCounts(0, 0, 0, 0, 0, 0);

        public AppointmentCounts plus(AppointmentCounts o) {
            return new AppointmentCounts(total + o.total, pending + o.pending, confirmed + o.confirmed,
                    completed + o.completed, cancelled + o.cancelled, noShow + o.noShow);
        }
    }

    public record Doctor(UUID userId, String fullName, String specialty) {
    }
}
