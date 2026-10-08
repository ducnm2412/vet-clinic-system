package com.vetclinic.order.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.scheduler.UnpaidOnlineOrderExpiryJob;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Job tự huỷ đơn quá hạn chạy trên PostgreSQL thật, với VNPAY giả (chỉ phần gọi mạng): đơn khách đã trả mà
 * thông báo bị lỡ phải được ghi nhận thay vì bị huỷ; cổng không trả lời thì đơn phải được giữ nguyên.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "order.mock-gateway.enabled=false",
        "order.vnpay.enabled=true",
        "order.vnpay.tmn-code=TESTTMN1",
        "order.vnpay.hash-secret=bi-mat-chi-de-test",
        "order.vnpay.return-url=http://127.0.0.1:3000/pay/vnpay-return"
})
@Transactional
class ExpiredOrderGatewayCheckFlowTest {

    private static final String TXN = "VC261007ABCD1A2B3C4D";

    @MockBean private VnpayHttp http;
    @Autowired private UnpaidOnlineOrderExpiryJob job;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

    private UUID orderId;

    @BeforeEach
    void insertExpiredUnpaidOnlineOrder() {
        orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, order_code, user_id, status, payment_method, recipient_name, recipient_phone,
                                    shipping_address, subtotal, shipping_fee, total, payment_status,
                                    payment_expires_at, gateway_txn_ref, gateway_txn_created_at)
                VALUES (?, 'VC261007-ABCD', ?, 'PENDING', 'ONLINE', 'Test', '0900000000', 'x',
                        300000, 30000, 330000, 'UNPAID', ?, ?, ?)""",
                orderId, UUID.randomUUID(),
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))), TXN,
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(40))));
    }

    private String queryResponse(String responseCode, String status) throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("vnp_ResponseId", "resp-1");
        f.put("vnp_Command", "querydr");
        f.put("vnp_ResponseCode", responseCode);
        f.put("vnp_Message", "QueryDr Success");
        f.put("vnp_TmnCode", "TESTTMN1");
        f.put("vnp_TxnRef", TXN);
        f.put("vnp_Amount", "00".equals(responseCode) ? "33000000" : "");
        f.put("vnp_BankCode", "NCB");
        f.put("vnp_PayDate", "20261007100500");
        f.put("vnp_TransactionNo", "14123456");
        f.put("vnp_TransactionType", "00".equals(responseCode) ? "01" : "");
        f.put("vnp_TransactionStatus", status);
        f.put("vnp_OrderInfo", "Thanh toan don hang");
        f.put("vnp_PromotionCode", "");
        f.put("vnp_PromotionAmount", "");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("bi-mat-chi-de-test".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        f.put("vnp_SecureHash", HexFormat.of().formatHex(
                mac.doFinal(String.join("|", f.values()).getBytes(StandardCharsets.UTF_8))));
        return new ObjectMapper().writeValueAsString(f);
    }

    private String column(String name) {
        em.flush();
        return jdbc.queryForObject("select " + name + " from orders where id = ?", String.class, orderId);
    }

    @Test
    void paidAtTheGatewayButNoticeLost_isRecordedAsPaidNotCancelled() throws Exception {
        when(http.postJson(anyString(), anyString())).thenReturn(queryResponse("00", "00"));

        job.run();

        assertThat(column("payment_status")).isEqualTo("PAID");
        assertThat(column("status")).isEqualTo("PENDING");
        assertThat(column("paid_at")).isNotNull();
    }

    @Test
    void neverPaidAtTheGateway_isCancelled() throws Exception {
        when(http.postJson(anyString(), anyString())).thenReturn(queryResponse("91", ""));

        job.run();

        assertThat(column("status")).isEqualTo("CANCELLED");
        assertThat(column("payment_status")).isEqualTo("UNPAID");
    }

    @Test
    void gatewayUnreachable_leavesTheOrderUntouchedForTheNextRun() throws Exception {
        when(http.postJson(anyString(), anyString())).thenThrow(new IOException("connection refused"));

        job.run();

        assertThat(column("status")).isEqualTo("PENDING");
        assertThat(column("payment_status")).isEqualTo("UNPAID");
    }

    @Test
    void expiredOnlySecondsAgo_isNotTouchedYet() throws Exception {
        jdbc.update("update orders set payment_expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(20)), orderId);
        when(http.postJson(anyString(), anyString())).thenReturn(queryResponse("91", ""));

        job.run();

        // Còn trong thời gian chờ để cổng chốt giao dịch sát giờ.
        assertThat(column("status")).isEqualTo("PENDING");
    }
}
