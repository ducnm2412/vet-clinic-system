package com.vetclinic.order.repository;

import com.vetclinic.order.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Danh sách đơn của khách chạy trên PostgreSQL thật: hoá đơn quầy huỷ khi chưa thu không được hiện. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@Transactional
class CustomerOrderListQueryTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderRepository orders;

    private void insert(UUID userId, String code, String status, String channel, String paymentStatus) {
        jdbc.update("""
                INSERT INTO orders (id, order_code, user_id, status, channel, payment_status, recipient_name,
                                    recipient_phone, shipping_address, subtotal, shipping_fee, exam_amount, total)
                VALUES (?, ?, ?, ?, ?, ?, 'Test', '', 'x', 0, 0, 0, 0)""",
                UUID.randomUUID(), code, userId, status, channel, paymentStatus);
    }

    @Test
    void cancelledUnpaidCounterInvoicesAreHiddenFromTheCustomer() {
        UUID me = UUID.randomUUID();
        insert(me, "T-ONLINE", "PENDING", "ONLINE", "UNPAID");
        insert(me, "T-ONLINE-CANCELLED", "CANCELLED", "ONLINE", "UNPAID");
        insert(me, "T-WAITING", "PENDING", "COUNTER", "UNPAID");
        insert(me, "T-PAID", "COMPLETED", "COUNTER", "PAID");
        insert(me, "T-WAIT-CANCELLED", "CANCELLED", "COUNTER", "UNPAID");
        insert(UUID.randomUUID(), "T-OTHER", "PENDING", "ONLINE", "UNPAID");

        var page = orders.findByUserIdOrderByCreatedAtDesc(me, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Order::getOrderCode)
                .containsExactlyInAnyOrder("T-ONLINE", "T-ONLINE-CANCELLED", "T-WAITING", "T-PAID");
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    // ---------- Đơn đã giao quá lâu ----------

    private UUID insertShipping(String code, String channel, String shippedAgo) {
        UUID userId = UUID.randomUUID();
        insert(userId, code, "SHIPPING", channel, "UNPAID");
        UUID orderId = jdbc.queryForObject("SELECT id FROM orders WHERE order_code = ?", UUID.class, code);
        jdbc.update("INSERT INTO order_status_history (id, order_id, from_status, to_status, created_at) "
                + "VALUES (?, ?, 'CONFIRMED', 'SHIPPING', now() - CAST(? AS interval))",
                UUID.randomUUID(), orderId, shippedAgo);
        return orderId;
    }

    @Test
    void shippingOrdersAreFoundOnlyOnceTheyHaveBeenShippingLongEnough() {
        UUID old = insertShipping("T-OLD", "ONLINE", "8 days");
        insertShipping("T-RECENT", "ONLINE", "2 days");
        insertShipping("T-COUNTER", "COUNTER", "30 days");
        insert(UUID.randomUUID(), "T-DONE", "COMPLETED", "ONLINE", "PAID");

        var ids = orders.findShippingOrderIdsShippedBefore(
                java.time.Instant.now().minus(java.time.Duration.ofDays(7)), PageRequest.ofSize(10));

        assertThat(ids).containsExactly(old);
    }
}
