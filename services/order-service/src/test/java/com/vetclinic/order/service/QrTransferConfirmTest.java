package com.vetclinic.order.service;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.OrderStatusHistory;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.PaymentResultResponse;
import com.vetclinic.order.dto.PaymentResultResponse.Outcome;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.payment.MockPaymentGateway;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.payment.QrTransferGateway;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nhân viên xác nhận đã nhận tiền chuyển khoản QR của đơn online. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrTransferConfirmTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private ObjectProvider<OnlinePaymentGateway> gatewayProvider;
    @Mock private ObjectProvider<MockPaymentGateway> mockGatewayProvider;

    private OnlinePaymentService service;
    private UUID staffId;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OnlinePaymentService(orderRepository, historyRepository, gatewayProvider, mockGatewayProvider);
        when(gatewayProvider.getIfAvailable()).thenReturn(new QrTransferGateway("/pay/qr"));

        staffId = UUID.randomUUID();
        order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("VC261007-ABCD")
                .userId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.ONLINE)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentExpiresAt(Instant.now().plus(Duration.ofMinutes(30)))
                .total(new BigDecimal("330000.00"))
                .build();
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.findByGatewayTxnRefForUpdate(any())).thenAnswer(inv ->
                inv.getArgument(0).equals(order.getGatewayTxnRef()) ? Optional.of(order) : Optional.empty());
    }

    @Test
    void confirm_marksPaidAndRecordsWhoConfirmed() {
        order.setGatewayTxnRef("VC261007ABCDXXXX");

        PaymentResultResponse result = service.confirmTransferReceived(staffId, order.getId());

        assertThat(result.outcome()).isEqualTo(Outcome.PAID);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        // Một dòng ghi nhận thanh toán (không có người) và một dòng ghi rõ nhân viên nào xác nhận.
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(history.capture());
        List<OrderStatusHistory> saved = history.getAllValues();
        assertThat(saved).anyMatch(h -> staffId.equals(h.getChangedBy()));
    }

    @Test
    void confirm_whenCustomerNeverOpenedQr_stillIssuesTxnRefAndPays() {
        assertThat(order.getGatewayTxnRef()).isNull();

        PaymentResultResponse result = service.confirmTransferReceived(staffId, order.getId());

        assertThat(result.outcome()).isEqualTo(Outcome.PAID);
        assertThat(order.getGatewayTxnRef()).startsWith("VC261007ABCD").matches("[A-Za-z0-9]+");
    }

    @Test
    void confirm_twice_isIdempotent() {
        order.setGatewayTxnRef("VC261007ABCDXXXX");
        service.confirmTransferReceived(staffId, order.getId());
        Instant paidAt = order.getPaidAt();

        PaymentResultResponse second = service.confirmTransferReceived(staffId, order.getId());

        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY_PAID);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void confirm_cancelledOrder_isRejected() {
        order.setStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> service.confirmTransferReceived(staffId, order.getId()))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("huỷ");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void confirm_nonOnlineOrder_isRejected() {
        order.setPaymentMethod(PaymentMethod.COD);

        assertThatThrownBy(() -> service.confirmTransferReceived(staffId, order.getId()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void confirm_unknownOrder_is404() {
        assertThatThrownBy(() -> service.confirmTransferReceived(staffId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Cổng khác (giả lập/VNPAY) tự báo kết quả về; không để nhân viên ghi tay đè lên. */
    @Test
    void confirm_whenQrGatewayNotActive_isRejected() {
        when(gatewayProvider.getIfAvailable()).thenReturn(new MockPaymentGateway("s", "/pay/mock"));

        assertThatThrownBy(() -> service.confirmTransferReceived(staffId, order.getId()))
                .isInstanceOf(InvalidOrderStateException.class).hasMessageContaining("QR");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }
}
