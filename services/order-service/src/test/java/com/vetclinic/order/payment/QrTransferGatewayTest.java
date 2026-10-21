package com.vetclinic.order.payment;

import com.vetclinic.order.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrTransferGatewayTest {

    private final QrTransferGateway gateway = new QrTransferGateway("/pay/qr");

    @Test
    void buildPaymentUrl_pointsToQrPageWithOrderId() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).orderCode("VC261007-ABCD").build();

        assertThat(gateway.buildPaymentUrl(order, "TXN1", "127.0.0.1")).isEqualTo("/pay/qr?orderId=" + id);
    }

    @Test
    void buildPaymentUrl_respectsExistingQueryInPageUrl() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).build();

        assertThat(new QrTransferGateway("/pay/qr?lang=vi").buildPaymentUrl(order, "TXN1", "127.0.0.1"))
                .isEqualTo("/pay/qr?lang=vi&orderId=" + id);
    }

    /** Callback công khai không có chữ ký nào để tin, nên phải từ chối mọi thứ — kể cả khi trông như "đã trả". */
    @Test
    void verifyCallback_alwaysRejects() {
        assertThatThrownBy(() -> gateway.verifyCallback(Map.of("txnRef", "TXN1", "amount", "330000", "status", "SUCCESS")))
                .isInstanceOf(InvalidGatewaySignatureException.class);
        assertThatThrownBy(() -> gateway.verifyCallback(null))
                .isInstanceOf(InvalidGatewaySignatureException.class);
    }

    @Test
    void doesNotSupportQueryingTransaction() {
        assertThat(gateway.queryTransaction(Order.builder().build())).isEmpty();
    }

    @Test
    void qrGatewayCannotBeEnabledTogetherWithAnotherGateway() {
        assertThatThrownBy(() -> new OnlineGatewayExclusivityCheck(
                List.of(gateway, new MockPaymentGateway("test-secret", "/pay/mock"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("order.qr-gateway.enabled");
        // Một cổng một mình thì không sao.
        new OnlineGatewayExclusivityCheck(List.of(gateway));
    }
}
