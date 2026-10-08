package com.vetclinic.order.service;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.dto.PaymentResultResponse;
import com.vetclinic.order.dto.PaymentResultResponse.Outcome;
import com.vetclinic.order.payment.GatewayQueryException;
import com.vetclinic.order.payment.GatewayResult;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpiredOnlineOrderProcessorTest {

    @Mock private OrderService orderService;
    @Mock private OnlinePaymentService onlinePaymentService;
    @Mock private ObjectProvider<OnlinePaymentGateway> gatewayProvider;
    @Mock private OnlinePaymentGateway gateway;

    private ExpiredOnlineOrderProcessor processor;
    private Order order;

    @BeforeEach
    void setUp() {
        processor = new ExpiredOnlineOrderProcessor(orderService, onlinePaymentService, gatewayProvider);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);

        order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("VC261007-ABCD")
                .gatewayTxnRef("VC261007ABCD1A2B3C4D")
                .gatewayTxnCreatedAt(Instant.now().minus(Duration.ofMinutes(40)))
                .paymentExpiresAt(Instant.now().minus(Duration.ofMinutes(10)))
                .total(new BigDecimal("330000"))
                .build();
        when(orderService.findExpiredUnpaidOnlineOrder(order.getId())).thenReturn(Optional.of(order));
        when(orderService.cancelExpiredUnpaidOrder(order.getId())).thenReturn(true);
    }

    private static GatewayResult remote(boolean success) {
        return new GatewayResult("VC261007ABCD1A2B3C4D", new BigDecimal("330000"), success, "14123456");
    }

    private static PaymentResultResponse recorded(Outcome outcome) {
        return new PaymentResultResponse(outcome, UUID.randomUUID(), "VC261007-ABCD", null);
    }

    @Test
    void gatewayAlreadyHasThePayment_recordsItAndDoesNotCancel() {
        when(gateway.queryTransaction(order)).thenReturn(Optional.of(remote(true)));
        when(onlinePaymentService.recordGatewayResult(any())).thenReturn(recorded(Outcome.PAID));

        assertThat(processor.process(order.getId())).isFalse();

        verify(onlinePaymentService).recordGatewayResult(remote(true));
        verify(orderService, never()).cancelExpiredUnpaidOrder(any());
    }

    @Test
    void gatewayHasNoTransaction_cancelsTheOrder() {
        when(gateway.queryTransaction(order)).thenReturn(Optional.empty());

        assertThat(processor.process(order.getId())).isTrue();

        verify(onlinePaymentService, never()).recordGatewayResult(any());
        verify(orderService).cancelExpiredUnpaidOrder(order.getId());
    }

    @Test
    void gatewayHasAnUnpaidTransaction_cancelsTheOrder() {
        when(gateway.queryTransaction(order)).thenReturn(Optional.of(remote(false)));

        assertThat(processor.process(order.getId())).isTrue();

        verify(onlinePaymentService, never()).recordGatewayResult(any());
    }

    @Test
    void gatewayCannotBeReached_keepsTheOrderAndRetriesLater() {
        when(gateway.queryTransaction(order)).thenThrow(new GatewayQueryException("hết thời gian chờ"));

        assertThat(processor.process(order.getId())).isFalse();

        verify(orderService, never()).cancelExpiredUnpaidOrder(any());
    }

    @Test
    void gatewayDownForDays_eventuallyCancelsInsteadOfHangingForever() {
        order.setPaymentExpiresAt(Instant.now().minus(ExpiredOnlineOrderProcessor.GATEWAY_PATIENCE).minusSeconds(60));
        when(gateway.queryTransaction(order)).thenThrow(new GatewayQueryException("hết thời gian chờ"));

        assertThat(processor.process(order.getId())).isTrue();
    }

    @Test
    void gatewayReportsPaymentButItCannotBeRecorded_stillCancelsBecauseTheOrderIsNotPaid() {
        // Ví dụ sai số tiền: đơn không được tính là đã trả nên không thể để treo mãi.
        when(gateway.queryTransaction(order)).thenReturn(Optional.of(remote(true)));
        when(onlinePaymentService.recordGatewayResult(any())).thenReturn(recorded(Outcome.AMOUNT_MISMATCH));

        assertThat(processor.process(order.getId())).isTrue();
    }

    @Test
    void ipnArrivedWhileWeWereAsking_isTreatedAsPaid() {
        when(gateway.queryTransaction(order)).thenReturn(Optional.of(remote(true)));
        when(onlinePaymentService.recordGatewayResult(any())).thenReturn(recorded(Outcome.ALREADY_PAID));

        assertThat(processor.process(order.getId())).isFalse();
        verify(orderService, never()).cancelExpiredUnpaidOrder(any());
    }

    @Test
    void orderNoLongerQualifies_isLeftAlone() {
        when(orderService.findExpiredUnpaidOnlineOrder(order.getId())).thenReturn(Optional.empty());

        assertThat(processor.process(order.getId())).isFalse();

        verify(gateway, never()).queryTransaction(any());
        verify(orderService, never()).cancelExpiredUnpaidOrder(any());
    }

    @Test
    void noGatewayEnabledOrNeverGotALink_cancelsWithoutAsking() {
        when(gatewayProvider.getIfAvailable()).thenReturn(null);
        assertThat(processor.process(order.getId())).isTrue();

        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        order.setGatewayTxnRef(null);
        assertThat(processor.process(order.getId())).isTrue();

        verify(gateway, never()).queryTransaction(any());
    }
}
