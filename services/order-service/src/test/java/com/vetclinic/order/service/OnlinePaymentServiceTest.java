package com.vetclinic.order.service;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.OrderStatusHistory;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.MockPaymentSubmitRequest;
import com.vetclinic.order.dto.PaymentInitResponse;
import com.vetclinic.order.dto.PaymentResultResponse;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.payment.GatewayUnavailableException;
import com.vetclinic.order.payment.InvalidGatewaySignatureException;
import com.vetclinic.order.payment.MockPaymentGateway;
import com.vetclinic.order.payment.MockPaymentGateway.Outcome;
import com.vetclinic.order.payment.MockPaymentGatewayTest;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlinePaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private ObjectProvider<OnlinePaymentGateway> gatewayProvider;
    @Mock private ObjectProvider<MockPaymentGateway> mockGatewayProvider;

    private final MockPaymentGateway gateway = new MockPaymentGateway("test-secret", "/pay/mock");
    private OnlinePaymentService service;

    private UUID userId;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OnlinePaymentService(orderRepository, historyRepository, gatewayProvider, mockGatewayProvider);
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(mockGatewayProvider.getIfAvailable()).thenReturn(gateway);

        userId = UUID.randomUUID();
        order = onlineOrder(userId);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.findByGatewayTxnRef(any())).thenAnswer(inv ->
                inv.getArgument(0).equals(order.getGatewayTxnRef()) ? Optional.of(order) : Optional.empty());
        when(orderRepository.findByGatewayTxnRefForUpdate(any())).thenAnswer(inv ->
                inv.getArgument(0).equals(order.getGatewayTxnRef()) ? Optional.of(order) : Optional.empty());
    }

    private static Order onlineOrder(UUID userId) {
        return Order.builder()
                .id(UUID.randomUUID())
                .orderCode("VC261007-ABCD")
                .userId(userId)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentExpiresAt(Instant.now().plus(Duration.ofMinutes(30)))
                .total(new BigDecimal("330000.00"))
                .build();
    }

    private Map<String, String> signed(String outcome) {
        return gateway.buildCallback(order.getGatewayTxnRef(), "330000", Outcome.valueOf(outcome));
    }

    // ---------- startPayment ----------

    @Test
    void startPayment_issuesSignedLinkAndRemembersTxnRef() {
        PaymentInitResponse response = service.startPayment(userId, order.getId(), "127.0.0.1");

        assertThat(order.getGatewayTxnRef()).startsWith("VC261007ABCD").matches("[A-Za-z0-9]+");
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.paymentUrl()).contains("txnRef=" + order.getGatewayTxnRef()).contains("signature=");
        assertThat(response.expiresAt()).isEqualTo(order.getPaymentExpiresAt());
        // Mốc cấp link được lưu (theo giây) để hỏi lại giao dịch sau này đúng mốc cổng ghi.
        assertThat(order.getGatewayTxnCreatedAt()).isNotNull().isEqualTo(
                order.getGatewayTxnCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void startPayment_again_replacesPreviousTxnRef() {
        service.startPayment(userId, order.getId(), "127.0.0.1");
        String first = order.getGatewayTxnRef();

        service.startPayment(userId, order.getId(), "127.0.0.1");

        assertThat(order.getGatewayTxnRef()).isNotEqualTo(first);
        // Mã cũ không còn khớp đơn nào nên kết quả muộn của link cũ bị coi là giao dịch lạ.
        assertThat(orderRepository.findByGatewayTxnRefForUpdate(first)).isEmpty();
    }

    @Test
    void startPayment_ofSomeoneElsesOrder_isNotFound() {
        assertThatThrownBy(() -> service.startPayment(UUID.randomUUID(), order.getId(), "127.0.0.1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startPayment_rejectsOrdersThatCannotBePaidOnline() {
        order.setPaymentMethod(PaymentMethod.COD);
        assertThatThrownBy(() -> service.startPayment(userId, order.getId(), "127.0.0.1"))
                .isInstanceOf(InvalidOrderStateException.class);

        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(PaymentStatus.PAID);
        assertThatThrownBy(() -> service.startPayment(userId, order.getId(), "127.0.0.1"))
                .isInstanceOf(InvalidOrderStateException.class);

        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.startPayment(userId, order.getId(), "127.0.0.1"))
                .isInstanceOf(InvalidOrderStateException.class);

        order.setStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.startPayment(userId, order.getId(), "127.0.0.1"))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void startPayment_afterDeadline_isRejected() {
        order.setPaymentExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.startPayment(userId, order.getId(), "127.0.0.1"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("quá hạn");
        assertThat(order.getGatewayTxnRef()).isNull();
    }

    @Test
    void startPayment_withoutGateway_isUnavailable() {
        when(gatewayProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.startPayment(userId, order.getId(), "127.0.0.1"))
                .isInstanceOf(GatewayUnavailableException.class);
    }

    // ---------- handleCallback ----------

    @Test
    void callback_success_marksOrderPaidAndKeepsStatus() {
        service.startPayment(userId, order.getId(), "127.0.0.1");

        PaymentResultResponse result = service.handleCallback(signed("SUCCESS"));

        assertThat(result.outcome()).isEqualTo(PaymentResultResponse.Outcome.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        // Trả tiền xong đơn vẫn chờ nhân viên xác nhận, không tự nhảy trạng thái.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getNote()).contains("thanh toán online");
    }

    @Test
    void callback_repeated_isIdempotent() {
        service.startPayment(userId, order.getId(), "127.0.0.1");
        service.handleCallback(signed("SUCCESS"));
        Instant paidAt = order.getPaidAt();

        PaymentResultResponse again = service.handleCallback(signed("SUCCESS"));

        assertThat(again.outcome()).isEqualTo(PaymentResultResponse.Outcome.ALREADY_PAID);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        verify(historyRepository, org.mockito.Mockito.times(1)).save(any(OrderStatusHistory.class));
    }

    @Test
    void callback_failedOrCancelled_leavesOrderUnpaidSoCustomerCanRetry() {
        service.startPayment(userId, order.getId(), "127.0.0.1");

        PaymentResultResponse failed = service.handleCallback(signed("FAILED"));
        PaymentResultResponse cancelled = service.handleCallback(signed("CANCELLED"));

        assertThat(failed.outcome()).isEqualTo(PaymentResultResponse.Outcome.FAILED);
        assertThat(cancelled.outcome()).isEqualTo(PaymentResultResponse.Outcome.FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getPaidAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Trả lại lần sau vẫn được.
        service.startPayment(userId, order.getId(), "127.0.0.1");
        assertThat(service.handleCallback(signed("SUCCESS")).outcome()).isEqualTo(PaymentResultResponse.Outcome.PAID);
    }

    @Test
    void callback_withBadSignature_isRejectedAndChangesNothing() {
        service.startPayment(userId, order.getId(), "127.0.0.1");
        Map<String, String> forged = new LinkedHashMap<>(signed("FAILED"));
        forged.put("status", "SUCCESS");

        assertThatThrownBy(() -> service.handleCallback(forged)).isInstanceOf(InvalidGatewaySignatureException.class);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        // Chỉ lần lưu mã giao dịch của startPayment; kết quả giả mạo không được ghi gì thêm.
        verify(orderRepository, org.mockito.Mockito.times(1)).save(any(Order.class));
        verify(historyRepository, never()).save(any(OrderStatusHistory.class));
    }

    @Test
    void callback_withDifferentAmount_isNotAccepted() {
        service.startPayment(userId, order.getId(), "127.0.0.1");

        PaymentResultResponse result = service.handleCallback(
                gateway.buildCallback(order.getGatewayTxnRef(), "1000", Outcome.SUCCESS));

        assertThat(result.outcome()).isEqualTo(PaymentResultResponse.Outcome.AMOUNT_MISMATCH);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void callback_forUnknownTransaction_isAcknowledgedWithoutEffect() {
        PaymentResultResponse result = service.handleCallback(
                gateway.buildCallback("VC000000-XXXX-UNKNOWN", "330000", Outcome.SUCCESS));

        assertThat(result.outcome()).isEqualTo(PaymentResultResponse.Outcome.UNKNOWN_TRANSACTION);
        assertThat(result.orderId()).isNull();
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void callback_successAfterOrderWasCancelled_isRecordedForManualRefund() {
        service.startPayment(userId, order.getId(), "127.0.0.1");
        order.setStatus(OrderStatus.CANCELLED);

        PaymentResultResponse result = service.handleCallback(signed("SUCCESS"));

        assertThat(result.outcome()).isEqualTo(PaymentResultResponse.Outcome.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getNote()).contains("hoàn tiền");
    }

    @Test
    void callback_withoutGateway_isUnavailable() {
        when(gatewayProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.handleCallback(Map.of("txnRef", "x")))
                .isInstanceOf(GatewayUnavailableException.class);
    }

    // ---------- cổng giả lập ----------

    private MockPaymentSubmitRequest submitFor(String url, Outcome outcome) {
        Map<String, String> q = MockPaymentGatewayTest.query(url);
        return new MockPaymentSubmitRequest(q.get("txnRef"), q.get("orderCode"), q.get("amount"),
                q.get("signature"), outcome);
    }

    @Test
    void mockSubmit_success_goesThroughSameCallbackPathAndMarksPaid() {
        String url = service.startPayment(userId, order.getId(), "127.0.0.1").paymentUrl();

        PaymentResultResponse result = service.submitMockPayment(userId, submitFor(url, Outcome.SUCCESS));

        assertThat(result.outcome()).isEqualTo(PaymentResultResponse.Outcome.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void mockSubmit_cancel_keepsOrderUnpaid() {
        String url = service.startPayment(userId, order.getId(), "127.0.0.1").paymentUrl();

        PaymentResultResponse result = service.submitMockPayment(userId, submitFor(url, Outcome.CANCELLED));

        assertThat(result.outcome()).isEqualTo(PaymentResultResponse.Outcome.FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void mockSubmit_withEditedLink_isRejected() {
        String url = service.startPayment(userId, order.getId(), "127.0.0.1").paymentUrl();
        MockPaymentSubmitRequest real = submitFor(url, Outcome.SUCCESS);
        MockPaymentSubmitRequest cheaper = new MockPaymentSubmitRequest(
                real.txnRef(), real.orderCode(), "1", real.signature(), Outcome.SUCCESS);

        assertThatThrownBy(() -> service.submitMockPayment(userId, cheaper))
                .isInstanceOf(InvalidGatewaySignatureException.class);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void mockSubmit_byAnotherCustomer_isNotFound() {
        String url = service.startPayment(userId, order.getId(), "127.0.0.1").paymentUrl();

        assertThatThrownBy(() -> service.submitMockPayment(UUID.randomUUID(), submitFor(url, Outcome.SUCCESS)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void mockSubmit_ofReplacedLink_isNotFound() {
        String oldUrl = service.startPayment(userId, order.getId(), "127.0.0.1").paymentUrl();
        service.startPayment(userId, order.getId(), "127.0.0.1");

        assertThatThrownBy(() -> service.submitMockPayment(userId, submitFor(oldUrl, Outcome.SUCCESS)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mockSubmit_whenMockDisabled_isUnavailable() {
        String url = service.startPayment(userId, order.getId(), "127.0.0.1").paymentUrl();
        when(mockGatewayProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.submitMockPayment(userId, submitFor(url, Outcome.SUCCESS)))
                .isInstanceOf(GatewayUnavailableException.class);
    }
}
