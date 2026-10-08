package com.vetclinic.order.payment;

import com.vetclinic.order.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway("test-secret", "/pay/mock");

    @Test
    void blankSecret_isRejected() {
        assertThatThrownBy(() -> new MockPaymentGateway(" ", "/pay/mock"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new MockPaymentGateway(null, "/pay/mock"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paymentUrl_carriesSignedTxnRefOrderCodeAndWholeDongAmount() {
        Order order = Order.builder().orderCode("VC261007-ABCD").total(new BigDecimal("330000.00")).build();

        String url = gateway.buildPaymentUrl(order, "VC261007ABCD1A2B3C4D", "127.0.0.1");

        Map<String, String> q = query(url);
        assertThat(url).startsWith("/pay/mock?");
        assertThat(q).containsEntry("txnRef", "VC261007ABCD1A2B3C4D")
                .containsEntry("orderCode", "VC261007-ABCD")
                .containsEntry("amount", "330000");
        gateway.verifyPaymentRequest(q.get("txnRef"), q.get("orderCode"), q.get("amount"), q.get("signature"));
    }

    @Test
    void paymentRequest_withEditedAmountOrTxnRef_isRejected() {
        Order order = Order.builder().orderCode("VC261007-ABCD").total(new BigDecimal("330000")).build();
        Map<String, String> q = query(gateway.buildPaymentUrl(order, "REF-1", "127.0.0.1"));

        assertThatThrownBy(() -> gateway.verifyPaymentRequest("REF-1", "VC261007-ABCD", "1000", q.get("signature")))
                .isInstanceOf(InvalidGatewaySignatureException.class);
        assertThatThrownBy(() -> gateway.verifyPaymentRequest("REF-2", "VC261007-ABCD", "330000", q.get("signature")))
                .isInstanceOf(InvalidGatewaySignatureException.class);
    }

    @Test
    void callback_roundTrip_returnsParsedResult() {
        Map<String, String> params = gateway.buildCallback("REF-1", "330000", MockPaymentGateway.Outcome.SUCCESS);

        GatewayResult result = gateway.verifyCallback(params);

        assertThat(result.txnRef()).isEqualTo("REF-1");
        assertThat(result.amount()).isEqualByComparingTo("330000");
        assertThat(result.success()).isTrue();
        assertThat(result.gatewayTransactionNo()).startsWith("MOCK");
    }

    @Test
    void callback_failedAndCancelled_areNotSuccess() {
        for (MockPaymentGateway.Outcome outcome :
                new MockPaymentGateway.Outcome[]{MockPaymentGateway.Outcome.FAILED, MockPaymentGateway.Outcome.CANCELLED}) {
            assertThat(gateway.verifyCallback(gateway.buildCallback("REF-1", "100", outcome)).success()).isFalse();
        }
    }

    @Test
    void callback_isIndependentOfParameterOrder() {
        Map<String, String> params = gateway.buildCallback("REF-1", "330000", MockPaymentGateway.Outcome.SUCCESS);
        Map<String, String> reversed = new LinkedHashMap<>();
        params.keySet().stream().sorted(java.util.Comparator.reverseOrder())
                .forEach(k -> reversed.put(k, params.get(k)));

        assertThat(gateway.verifyCallback(reversed).success()).isTrue();
    }

    @Test
    void callback_tamperedAmountStatusOrSignature_isRejected() {
        Map<String, String> params = gateway.buildCallback("REF-1", "330000", MockPaymentGateway.Outcome.FAILED);

        Map<String, String> cheaper = new LinkedHashMap<>(params);
        cheaper.put("amount", "1");
        Map<String, String> forcedSuccess = new LinkedHashMap<>(params);
        forcedSuccess.put("status", "SUCCESS");
        Map<String, String> noSignature = new LinkedHashMap<>(params);
        noSignature.remove("signature");
        Map<String, String> wrongSignature = new LinkedHashMap<>(params);
        wrongSignature.put("signature", "deadbeef");

        for (Map<String, String> bad : java.util.List.of(cheaper, forcedSuccess, noSignature, wrongSignature)) {
            assertThatThrownBy(() -> gateway.verifyCallback(bad))
                    .isInstanceOf(InvalidGatewaySignatureException.class);
        }
        assertThatThrownBy(() -> gateway.verifyCallback(null)).isInstanceOf(InvalidGatewaySignatureException.class);
    }

    @Test
    void callback_signedWithAnotherSecret_isRejected() {
        MockPaymentGateway other = new MockPaymentGateway("another-secret", "/pay/mock");
        Map<String, String> forged = other.buildCallback("REF-1", "330000", MockPaymentGateway.Outcome.SUCCESS);

        assertThatThrownBy(() -> gateway.verifyCallback(forged)).isInstanceOf(InvalidGatewaySignatureException.class);
    }

    public static Map<String, String> query(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams()
                .forEach((k, v) -> result.put(k, java.net.URLDecoder.decode(v.get(0), java.nio.charset.StandardCharsets.UTF_8)));
        return result;
    }
}
