package com.vetclinic.order.repository;

import com.vetclinic.order.dto.OrderDailyStatsResponse;
import com.vetclinic.order.service.OrderStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Câu SQL thống kê chạy trên PostgreSQL thật (order_db_test): múi giờ Việt Nam và việc không tính
 * trùng tiền khám không kiểm được bằng mock. Mỗi test tự rollback nên không để lại dữ liệu.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@Transactional
class OrderStatsQueryTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderStatsService stats;

    private void insert(String code, String status, String channel, String total, String examAmount,
                        Instant createdAt, Instant completedAt, Instant cancelledAt) {
        jdbc.update("""
                INSERT INTO orders (id, order_code, status, channel, recipient_name, recipient_phone, shipping_address,
                                    subtotal, shipping_fee, exam_amount, total, created_at, completed_at, cancelled_at)
                VALUES (?, ?, ?, ?, 'Test', '0900000000', 'x', 0, 0, ?::numeric, ?::numeric, ?, ?, ?)""",
                UUID.randomUUID(), code, status, channel, examAmount, total,
                Timestamp.from(createdAt),
                completedAt == null ? null : Timestamp.from(completedAt),
                cancelledAt == null ? null : Timestamp.from(cancelledAt));
    }

    private OrderDailyStatsResponse day(List<OrderDailyStatsResponse> rows, LocalDate date) {
        return rows.stream().filter(r -> r.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void examAmountOfACounterInvoiceIsNotCountedAsOrderRevenue() {
        Instant paid = Instant.parse("2026-10-05T03:00:00Z");   // 10:00 ngày 05/10 giờ Việt Nam
        insert("T-COUNTER", "COMPLETED", "COUNTER", "500000", "200000", paid, paid, null);

        List<OrderDailyStatsResponse> rows = stats.daily(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));

        // 500.000 gồm 200.000 tiền khám; payment-service đã tính khoản này nên đơn hàng chỉ ghi 300.000.
        assertThat(day(rows, LocalDate.of(2026, 10, 5)).revenue()).isEqualByComparingTo("300000");
    }

    @Test
    void ordersAreGroupedByVietnamDayNotUtcDay() {
        // 20:00 UTC ngày 05/10 đã là 03:00 ngày 06/10 ở Việt Nam.
        Instant lateUtc = Instant.parse("2026-10-05T20:00:00Z");
        insert("T-COD", "COMPLETED", "ONLINE", "330000", "0", lateUtc, lateUtc, null);

        List<OrderDailyStatsResponse> rows = stats.daily(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6));

        assertThat(rows).extracting(OrderDailyStatsResponse::date).containsExactly(LocalDate.of(2026, 10, 6));
        assertThat(day(rows, LocalDate.of(2026, 10, 6)).revenue()).isEqualByComparingTo("330000");
    }

    @Test
    void onlyCompletedOrdersCountAsRevenueAndCancelledAreCountedSeparately() {
        Instant t = Instant.parse("2026-10-05T03:00:00Z");
        insert("T-DONE", "COMPLETED", "ONLINE", "100000", "0", t, t, null);
        insert("T-PENDING", "PENDING", "ONLINE", "900000", "0", t, null, null);
        insert("T-CANCELLED", "CANCELLED", "ONLINE", "700000", "0", t, null, t);

        OrderDailyStatsResponse row = day(
                stats.daily(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)), LocalDate.of(2026, 10, 5));

        assertThat(row.revenue()).isEqualByComparingTo("100000");
        assertThat(row.ordersCreated()).isEqualTo(3);
        assertThat(row.ordersCompleted()).isEqualTo(1);
        assertThat(row.ordersCancelled()).isEqualTo(1);
    }
}
