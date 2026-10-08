package com.vetclinic.order.payment;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IPN và trang trả về của VNPAY đi qua HTTP thật (MockMvc, không token đăng nhập) vào PostgreSQL thật
 * (order_db_test): chữ ký, mã phản hồi cho VNPAY và trạng thái đơn trong DB. Mỗi test tự rollback.
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
@AutoConfigureMockMvc
@Transactional
class VnpayCallbackFlowTest {

    private static final String TXN = "VC261007ABCD1A2B3C4D";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private VnpayGateway gateway;

    private UUID orderId;

    @BeforeEach
    void insertUnpaidOnlineOrder() {
        orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders (id, order_code, user_id, status, payment_method, recipient_name, recipient_phone,
                                    shipping_address, subtotal, shipping_fee, total, payment_status,
                                    payment_expires_at, gateway_txn_ref)
                VALUES (?, 'VC261007-ABCD', ?, 'PENDING', 'ONLINE', 'Test', '0900000000', 'x',
                        300000, 30000, 330000, 'UNPAID', ?, ?)""",
                orderId, UUID.randomUUID(), Timestamp.from(Instant.now().plus(Duration.ofMinutes(20))), TXN);
    }

    /** Tham số "VNPAY gửi về" đã ký đúng. */
    private Map<String, String> signed(String amountX100, String responseCode, String status, String txnRef) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("vnp_TmnCode", "TESTTMN1");
        p.put("vnp_Amount", amountX100);
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_OrderInfo", "Thanh toan don hang VC261007ABCD");
        p.put("vnp_TransactionNo", "14123456");
        p.put("vnp_ResponseCode", responseCode);
        p.put("vnp_TransactionStatus", status);
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_PayDate", "20261007103000");
        p.put(VnpayGateway.HASH, gateway.sign(p));
        return p;
    }

    private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request, Map<String, String> params) {
        params.forEach(request::param);
        return request;
    }

    /** Đẩy thay đổi JPA xuống DB trước khi đọc bằng JDBC thuần (JDBC không tự kích hoạt flush của Hibernate). */
    private void flush() {
        em.flush();
    }

    private String paymentStatus() {
        flush();
        return jdbc.queryForObject("select payment_status from orders where id = ?", String.class, orderId);
    }

    private int historyRows() {
        flush();
        return jdbc.queryForObject("select count(*) from order_status_history where order_id = ?", Integer.class, orderId);
    }

    // ---------- IPN ----------

    @Test
    void ipn_successfulPayment_marksOrderPaidAndAnswers00() throws Exception {
        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), signed("33000000", "00", "00", TXN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));

        assertThat(paymentStatus()).isEqualTo("PAID");
        flush();
        assertThat(jdbc.queryForObject("select paid_at is not null from orders where id = ?", Boolean.class, orderId)).isTrue();
        // Trả tiền xong đơn vẫn chờ nhân viên xác nhận.
        assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, orderId)).isEqualTo("PENDING");
        assertThat(historyRows()).isEqualTo(1);
    }

    @Test
    void ipn_calledAgain_answers02AndChangesNothing() throws Exception {
        Map<String, String> params = signed("33000000", "00", "00", TXN);
        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), params)).andExpect(jsonPath("$.RspCode").value("00"));

        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), params))
                .andExpect(jsonPath("$.RspCode").value("02"))
                .andExpect(jsonPath("$.Message").value("Order already confirmed"));

        assertThat(paymentStatus()).isEqualTo("PAID");
        assertThat(historyRows()).isEqualTo(1);
    }

    @Test
    void ipn_customerCancelledOrFailed_answers00ButKeepsOrderUnpaid() throws Exception {
        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), signed("33000000", "24", "02", TXN)))
                .andExpect(jsonPath("$.RspCode").value("00"));

        assertThat(paymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void ipn_badSignature_answers97AndChangesNothing() throws Exception {
        Map<String, String> forged = signed("33000000", "24", "02", TXN);
        forged.put("vnp_ResponseCode", "00");
        forged.put("vnp_TransactionStatus", "00");

        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), forged))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));

        assertThat(paymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void ipn_withoutAnyParameters_answers97() throws Exception {
        mvc.perform(get("/orders/pay/vnpay/ipn")).andExpect(jsonPath("$.RspCode").value("97"));
    }

    @Test
    void ipn_unknownTransaction_answers01() throws Exception {
        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), signed("33000000", "00", "00", "KHONGCODONNAO")))
                .andExpect(jsonPath("$.RspCode").value("01"));
    }

    @Test
    void ipn_amountDiffersFromOrder_answers04AndDoesNotMarkPaid() throws Exception {
        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), signed("100000", "00", "00", TXN)))
                .andExpect(jsonPath("$.RspCode").value("04"));

        assertThat(paymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void ipn_afterOrderWasAutoCancelled_stillRecordsThePaymentForRefund() throws Exception {
        jdbc.update("update orders set status = 'CANCELLED', cancelled_at = now() where id = ?", orderId);

        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), signed("33000000", "00", "00", TXN)))
                .andExpect(jsonPath("$.RspCode").value("00"));

        // Huỷ nhưng đã trả = cần hoàn tiền thủ công.
        assertThat(paymentStatus()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select status from orders where id = ?", String.class, orderId)).isEqualTo("CANCELLED");
    }

    // ---------- trang trả về ----------

    @Test
    void return_successfulPayment_marksPaidWithoutAnyLogin() throws Exception {
        mvc.perform(withParams(get("/orders/pay/vnpay/return"), signed("33000000", "00", "00", TXN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PAID"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.orderCode").value("VC261007-ABCD"));

        assertThat(paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void return_afterIpnAlreadyArrived_isAlreadyPaid() throws Exception {
        Map<String, String> params = signed("33000000", "00", "00", TXN);
        mvc.perform(withParams(get("/orders/pay/vnpay/ipn"), params));

        mvc.perform(withParams(get("/orders/pay/vnpay/return"), params))
                .andExpect(jsonPath("$.outcome").value("ALREADY_PAID"));
        assertThat(historyRows()).isEqualTo(1);
    }

    @Test
    void return_customerCancelled_reportsFailureAndKeepsOrderUnpaid() throws Exception {
        mvc.perform(withParams(get("/orders/pay/vnpay/return"), signed("33000000", "24", "02", TXN)))
                .andExpect(jsonPath("$.outcome").value("FAILED"));

        assertThat(paymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void return_tamperedParameters_isRejectedWith400() throws Exception {
        Map<String, String> forged = signed("33000000", "24", "02", TXN);
        forged.put("vnp_ResponseCode", "00");
        forged.put("vnp_TransactionStatus", "00");

        mvc.perform(withParams(get("/orders/pay/vnpay/return"), forged)).andExpect(status().isBadRequest());

        assertThat(paymentStatus()).isEqualTo("UNPAID");
    }
}
