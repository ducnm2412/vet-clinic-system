package com.vetclinic.order.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayGatewayTest {

    private static final String TMN = "TESTTMN1";
    private static final String SECRET = "BI-MAT-SANDBOX-CHI-DE-TEST";
    private static final String RETURN_URL = "http://127.0.0.1:3000/pay/vnpay-return";

    private final VnpayGateway gateway = new VnpayGateway(TMN, SECRET,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", RETURN_URL);

    private static Order order(String total, Instant expires) {
        return Order.builder().orderCode("VC261007-ABCD").total(new BigDecimal(total))
                .paymentExpiresAt(expires).build();
    }

    /** Các tham số của link, đã giải mã. */
    private static Map<String, String> query(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().forEach((k, v) ->
                result.put(k, java.net.URLDecoder.decode(v.get(0), StandardCharsets.UTF_8)));
        return result;
    }

    /** Tự tính chữ ký theo đúng mô tả của VNPAY, độc lập với code đang test. */
    private static String hmac512(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    /** Dựng một kết quả "VNPAY gửi về" có chữ ký đúng. */
    private Map<String, String> callback(String txnRef, String amountX100, String responseCode, String status) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("vnp_TmnCode", TMN);
        p.put("vnp_Amount", amountX100);
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_OrderInfo", "Thanh toan don hang VC261007ABCD");
        p.put("vnp_TransactionNo", "14123456");
        p.put("vnp_ResponseCode", responseCode);
        p.put("vnp_TransactionStatus", status);
        p.put("vnp_TxnRef", txnRef);
        p.put(VnpayGateway.HASH, gateway.sign(p));
        return p;
    }

    @Test
    void missingConfiguration_failsFast() {
        assertThatThrownBy(() -> new VnpayGateway("", SECRET, "https://x", RETURN_URL))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tmn-code");
        assertThatThrownBy(() -> new VnpayGateway(TMN, " ", "https://x", RETURN_URL))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("hash-secret");
        assertThatThrownBy(() -> new VnpayGateway(TMN, SECRET, "https://x", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("return-url");
    }

    @Test
    void paymentUrl_hasTheRequiredParametersInVnpayFormat() {
        Instant now = Instant.parse("2026-10-07T03:00:00Z");              // 10:00:00 giờ Việt Nam
        Instant expires = Instant.parse("2026-10-07T03:30:00Z");

        String url = gateway.buildPaymentUrl(order("330000.00", expires), "VC261007ABCD1A2B3C4D", "203.0.113.7", now);

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String> q = query(url);
        assertThat(q).containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", TMN)
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_Locale", "vn")
                .containsEntry("vnp_TxnRef", "VC261007ABCD1A2B3C4D")
                .containsEntry("vnp_IpAddr", "203.0.113.7")
                .containsEntry("vnp_ReturnUrl", RETURN_URL)
                // Tiền nhân 100: 330.000 đ -> 33000000.
                .containsEntry("vnp_Amount", "33000000")
                // Giờ Việt Nam (GMT+7), hạn lấy đúng hạn của đơn.
                .containsEntry("vnp_CreateDate", "20261007100000")
                .containsEntry("vnp_ExpireDate", "20261007103000");
        assertThat(q.get("vnp_OrderInfo")).isEqualTo("Thanh toan don hang VC261007ABCD");
        assertThat(q).containsKey(VnpayGateway.HASH);
    }

    @Test
    void paymentUrl_signatureMatchesAnIndependentComputation() throws Exception {
        Instant now = Instant.parse("2026-10-07T03:00:00Z");
        String url = gateway.buildPaymentUrl(order("330000", Instant.parse("2026-10-07T03:30:00Z")),
                "REF1", "127.0.0.1", now);

        // Chuỗi cần ký theo tài liệu: tham số sắp theo tên, giá trị URL-encode (dấu cách thành '+').
        String expectedData = "vnp_Amount=33000000"
                + "&vnp_Command=pay"
                + "&vnp_CreateDate=20261007100000"
                + "&vnp_CurrCode=VND"
                + "&vnp_ExpireDate=20261007103000"
                + "&vnp_IpAddr=127.0.0.1"
                + "&vnp_Locale=vn"
                + "&vnp_OrderInfo=Thanh+toan+don+hang+VC261007ABCD"
                + "&vnp_OrderType=other"
                + "&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A3000%2Fpay%2Fvnpay-return"
                + "&vnp_TmnCode=" + TMN
                + "&vnp_TxnRef=REF1"
                + "&vnp_Version=2.1.0";

        assertThat(query(url).get(VnpayGateway.HASH)).isEqualTo(hmac512(expectedData));
    }

    @Test
    void paymentUrl_withoutAUsableDeadline_stillGetsAFutureExpiry() {
        Instant now = Instant.parse("2026-10-07T03:00:00Z");

        Map<String, String> noDeadline = query(gateway.buildPaymentUrl(order("1000", null), "R", "127.0.0.1", now));
        Map<String, String> pastDeadline = query(gateway.buildPaymentUrl(
                order("1000", Instant.parse("2026-10-07T02:00:00Z")), "R", "127.0.0.1", now));

        assertThat(noDeadline.get("vnp_ExpireDate")).isEqualTo("20261007101500");
        assertThat(pastDeadline.get("vnp_ExpireDate")).isEqualTo("20261007101500");
    }

    @Test
    void successfulCallback_isParsedWithAmountDividedBy100() {
        GatewayResult result = gateway.verifyCallback(callback("VC261007ABCD1A2B3C4D", "33000000", "00", "00"));

        assertThat(result.success()).isTrue();
        assertThat(result.txnRef()).isEqualTo("VC261007ABCD1A2B3C4D");
        assertThat(result.amount()).isEqualByComparingTo("330000");
        assertThat(result.gatewayTransactionNo()).isEqualTo("14123456");
    }

    @Test
    void callback_isNotSuccessUnlessBothCodesAreZeroZero() {
        // 24 = khách huỷ, 51 = không đủ số dư; 02 = giao dịch lỗi.
        assertThat(gateway.verifyCallback(callback("R", "100", "24", "02")).success()).isFalse();
        assertThat(gateway.verifyCallback(callback("R", "100", "51", "02")).success()).isFalse();
        assertThat(gateway.verifyCallback(callback("R", "100", "00", "02")).success()).isFalse();
        assertThat(gateway.verifyCallback(callback("R", "100", "00", "01")).success()).isFalse();
    }

    @Test
    void callback_acceptsUpperCaseHashAndIgnoresHashType() {
        Map<String, String> p = callback("R", "100", "00", "00");
        p.put(VnpayGateway.HASH, p.get(VnpayGateway.HASH).toUpperCase());
        p.put(VnpayGateway.HASH_TYPE, "HmacSHA512");

        assertThat(gateway.verifyCallback(p).success()).isTrue();
    }

    @Test
    void callback_withAnyFieldTampered_isRejected() {
        for (String field : new String[]{"vnp_Amount", "vnp_TxnRef", "vnp_ResponseCode", "vnp_TransactionStatus"}) {
            Map<String, String> p = callback("R", "100", "24", "02");
            p.put(field, field.equals("vnp_ResponseCode") || field.equals("vnp_TransactionStatus") ? "00" : "1");
            assertThatThrownBy(() -> gateway.verifyCallback(p))
                    .as(field).isInstanceOf(InvalidGatewaySignatureException.class);
        }
    }

    @Test
    void callback_withoutOrWithWrongSignature_isRejected() {
        Map<String, String> none = callback("R", "100", "00", "00");
        none.remove(VnpayGateway.HASH);
        Map<String, String> wrong = callback("R", "100", "00", "00");
        wrong.put(VnpayGateway.HASH, "deadbeef");

        assertThatThrownBy(() -> gateway.verifyCallback(none)).isInstanceOf(InvalidGatewaySignatureException.class);
        assertThatThrownBy(() -> gateway.verifyCallback(wrong)).isInstanceOf(InvalidGatewaySignatureException.class);
        assertThatThrownBy(() -> gateway.verifyCallback(null)).isInstanceOf(InvalidGatewaySignatureException.class);
    }

    @Test
    void callback_signedWithAnotherSecret_isRejected() {
        VnpayGateway other = new VnpayGateway(TMN, "mot-bi-mat-khac", "https://x", RETURN_URL);
        Map<String, String> p = callback("R", "100", "00", "00");
        p.put(VnpayGateway.HASH, other.sign(p));

        assertThatThrownBy(() -> gateway.verifyCallback(p)).isInstanceOf(InvalidGatewaySignatureException.class);
    }

    @Test
    void callback_forAnotherTerminal_isRejectedEvenWithValidSignature() {
        Map<String, String> p = callback("R", "100", "00", "00");
        p.put("vnp_TmnCode", "OTHERTMN");
        p.put(VnpayGateway.HASH, gateway.sign(p));

        assertThatThrownBy(() -> gateway.verifyCallback(p))
                .isInstanceOf(InvalidGatewaySignatureException.class).hasMessageContaining("terminal");
    }

    @Test
    void callback_withNonNumericAmountOrMissingTxnRef_isRejected() {
        Map<String, String> badAmount = callback("R", "mot-nghin", "00", "00");
        Map<String, String> noRef = callback("", "100", "00", "00");

        assertThatThrownBy(() -> gateway.verifyCallback(badAmount)).isInstanceOf(InvalidGatewaySignatureException.class);
        assertThatThrownBy(() -> gateway.verifyCallback(noRef)).isInstanceOf(InvalidGatewaySignatureException.class);
    }

    @Test
    void asciiOnly_stripsDiacriticsAndSpecialCharacters() {
        assertThat(VnpayGateway.asciiOnly("Đơn hàng số #1 - Hạt cho chó!")).isEqualTo("Don hang so 1  Hat cho cho");
    }

    @Test
    void onlyOneGatewayMayBeEnabled() {
        MockPaymentGateway mock = new MockPaymentGateway("secret", "/pay/mock");

        assertThatThrownBy(() -> new OnlineGatewayExclusivityCheck(java.util.List.of(mock, gateway)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("một cổng");
        new OnlineGatewayExclusivityCheck(java.util.List.of(gateway));
        new OnlineGatewayExclusivityCheck(java.util.List.of());
    }

    // ---------- hỏi lại giao dịch (querydr) ----------

    private static final String QUERY_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final Instant PAY_LINK_CREATED = Instant.parse("2026-10-07T03:00:00Z");   // 10:00:00 giờ Việt Nam

    /** Gateway có kết nối giả: ghi lại yêu cầu gửi đi và trả về phản hồi cho trước. */
    private static final class FakeHttp implements VnpayHttp {
        String url;
        String requestBody;
        String response;
        IOException failure;

        @Override
        public String postJson(String url, String json) throws IOException {
            this.url = url;
            this.requestBody = json;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private VnpayGateway gatewayWith(FakeHttp http) {
        return new VnpayGateway(TMN, SECRET, "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", RETURN_URL,
                QUERY_URL, "203.0.113.9", new ObjectMapper(), http);
    }

    private static Order paidLinkOrder() {
        return Order.builder().orderCode("VC261007-ABCD").total(new BigDecimal("330000"))
                .gatewayTxnRef("VC261007ABCD1A2B3C4D").gatewayTxnCreatedAt(PAY_LINK_CREATED).build();
    }

    /** Phản hồi querydr có chữ ký đúng; chữ ký tính độc lập, nối các trường bằng "|" theo thứ tự tài liệu. */
    private static String queryResponse(String responseCode, String status, String type, String txnRef, String amount)
            throws Exception {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("vnp_ResponseId", "resp-1");
        f.put("vnp_Command", "querydr");
        f.put("vnp_ResponseCode", responseCode);
        f.put("vnp_Message", "QueryDr Success");
        f.put("vnp_TmnCode", TMN);
        f.put("vnp_TxnRef", txnRef);
        f.put("vnp_Amount", amount);
        f.put("vnp_BankCode", "NCB");
        f.put("vnp_PayDate", "20261007100500");
        f.put("vnp_TransactionNo", "14123456");
        f.put("vnp_TransactionType", type);
        f.put("vnp_TransactionStatus", status);
        f.put("vnp_OrderInfo", "Thanh toan don hang");
        f.put("vnp_PromotionCode", "");
        f.put("vnp_PromotionAmount", "");
        f.put("vnp_SecureHash", hmac512(String.join("|", f.values())));
        return new ObjectMapper().writeValueAsString(f);
    }

    @Test
    void query_sendsASignedRequestInTheDocumentedFormat() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = queryResponse("00", "00", "01", "VC261007ABCD1A2B3C4D", "33000000");

        gatewayWith(http).queryTransaction(paidLinkOrder());

        assertThat(http.url).isEqualTo(QUERY_URL);
        JsonNode body = new ObjectMapper().readTree(http.requestBody);
        assertThat(body.get("vnp_Version").asText()).isEqualTo("2.1.0");
        assertThat(body.get("vnp_Command").asText()).isEqualTo("querydr");
        assertThat(body.get("vnp_TmnCode").asText()).isEqualTo(TMN);
        assertThat(body.get("vnp_TxnRef").asText()).isEqualTo("VC261007ABCD1A2B3C4D");
        assertThat(body.get("vnp_IpAddr").asText()).isEqualTo("203.0.113.9");
        // Phải là đúng mốc cấp link (giờ Việt Nam), không phải lúc hỏi.
        assertThat(body.get("vnp_TransactionDate").asText()).isEqualTo("20261007100000");
        assertThat(body.get("vnp_RequestId").asText()).hasSizeLessThanOrEqualTo(32).matches("[A-Za-z0-9]+");

        String expectedData = String.join("|", body.get("vnp_RequestId").asText(), "2.1.0", "querydr", TMN,
                "VC261007ABCD1A2B3C4D", "20261007100000", body.get("vnp_CreateDate").asText(), "203.0.113.9",
                body.get("vnp_OrderInfo").asText());
        assertThat(body.get("vnp_SecureHash").asText()).isEqualTo(hmac512(expectedData));
    }

    @Test
    void query_paidTransaction_isReportedAsSuccessWithAmountDividedBy100() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = queryResponse("00", "00", "01", "VC261007ABCD1A2B3C4D", "33000000");

        Optional<GatewayResult> result = gatewayWith(http).queryTransaction(paidLinkOrder());

        assertThat(result).isPresent();
        assertThat(result.get().success()).isTrue();
        assertThat(result.get().amount()).isEqualByComparingTo("330000");
        assertThat(result.get().txnRef()).isEqualTo("VC261007ABCD1A2B3C4D");
        assertThat(result.get().gatewayTransactionNo()).isEqualTo("14123456");
    }

    @Test
    void query_unfinishedFailedOrRefundedTransactions_areNotCountedAsPaid() throws Exception {
        for (String[] c : List.of(new String[]{"01", "01"}, new String[]{"02", "01"}, new String[]{"04", "01"},
                new String[]{"00", "02"}, new String[]{"00", "03"})) {
            FakeHttp http = new FakeHttp();
            http.response = queryResponse("00", c[0], c[1], "VC261007ABCD1A2B3C4D", "33000000");

            Optional<GatewayResult> result = gatewayWith(http).queryTransaction(paidLinkOrder());

            assertThat(result).as("status=%s type=%s", c[0], c[1]).isPresent();
            assertThat(result.get().success()).as("status=%s type=%s", c[0], c[1]).isFalse();
        }
    }

    @Test
    void query_transactionNotFound91_meansTheCustomerNeverPaid() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = queryResponse("91", "", "", "VC261007ABCD1A2B3C4D", "");

        assertThat(gatewayWith(http).queryTransaction(paidLinkOrder())).isEmpty();
    }

    @Test
    void query_otherErrorCodes_areUndeterminedNotNotFound() throws Exception {
        for (String code : List.of("02", "03", "94", "97", "99")) {
            FakeHttp http = new FakeHttp();
            http.response = queryResponse(code, "", "", "VC261007ABCD1A2B3C4D", "");

            assertThatThrownBy(() -> gatewayWith(http).queryTransaction(paidLinkOrder()))
                    .as(code).isInstanceOf(GatewayQueryException.class);
        }
    }

    @Test
    void query_tamperedOrForgedResponse_isNotTrusted() throws Exception {
        FakeHttp http = new FakeHttp();
        String genuineUnpaid = queryResponse("00", "02", "01", "VC261007ABCD1A2B3C4D", "33000000");
        http.response = genuineUnpaid.replace("\"vnp_TransactionStatus\":\"02\"", "\"vnp_TransactionStatus\":\"00\"");
        assertThat(http.response).isNotEqualTo(genuineUnpaid);

        assertThatThrownBy(() -> gatewayWith(http).queryTransaction(paidLinkOrder()))
                .isInstanceOf(GatewayQueryException.class).hasMessageContaining("chữ ký");

        http.response = "{}";
        assertThatThrownBy(() -> gatewayWith(http).queryTransaction(paidLinkOrder()))
                .isInstanceOf(GatewayQueryException.class);
    }

    @Test
    void query_responseForAnotherTransaction_isRejected() throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = queryResponse("00", "00", "01", "MAGIAODICHKHAC", "33000000");

        assertThatThrownBy(() -> gatewayWith(http).queryTransaction(paidLinkOrder()))
                .isInstanceOf(GatewayQueryException.class);
    }

    @Test
    void query_networkFailureOrGarbage_isUndetermined() {
        FakeHttp down = new FakeHttp();
        down.failure = new IOException("connection refused");
        FakeHttp garbage = new FakeHttp();
        garbage.response = "<html>502</html>";

        assertThatThrownBy(() -> gatewayWith(down).queryTransaction(paidLinkOrder()))
                .isInstanceOf(GatewayQueryException.class);
        assertThatThrownBy(() -> gatewayWith(garbage).queryTransaction(paidLinkOrder()))
                .isInstanceOf(GatewayQueryException.class);
    }

    @Test
    void query_orderWithoutALinkHasNothingToAsk() {
        FakeHttp http = new FakeHttp();
        Order neverPaid = Order.builder().orderCode("VC261007-ABCD").total(new BigDecimal("1000")).build();

        assertThat(gatewayWith(http).queryTransaction(neverPaid)).isEmpty();
        assertThat(http.requestBody).isNull();
    }

    @Test
    void paymentUrl_usesTheStoredLinkCreationTimeSoTheLaterQueryMatches() {
        Order order = paidLinkOrder();
        order.setPaymentExpiresAt(Instant.parse("2026-10-07T03:30:00Z"));

        String url = gateway.buildPaymentUrl(order, "VC261007ABCD1A2B3C4D", "127.0.0.1",
                Instant.parse("2026-10-07T05:00:00Z"));   // "bây giờ" khác mốc đã lưu

        assertThat(query(url).get("vnp_CreateDate")).isEqualTo("20261007100000");
    }
}
