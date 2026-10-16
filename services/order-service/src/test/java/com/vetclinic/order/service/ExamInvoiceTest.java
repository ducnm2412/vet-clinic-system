package com.vetclinic.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.client.PaymentClient;
import com.vetclinic.order.client.ProductClient;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.CounterInvoiceRequest;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.messaging.OrderCancelledEvent;
import com.vetclinic.order.messaging.PaymentAmountSetEvent;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.repository.CartRepository;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hoá đơn chờ thanh toán của khoản khám: lập từ sự kiện, lập bù khi nhân viên mở, và các luật chặn. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamInvoiceTest {

    private static final String TOKEN = "Bearer staff-token";

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartService cartService;
    @Mock private ProductClient productClient;
    @Mock private PaymentClient paymentClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ObjectProvider<OnlinePaymentGateway> onlineGateway;
    @Mock private CustomerInfoLookup customerInfoLookup;

    private OrderService orderService;
    private ExamInvoiceService examInvoiceService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID examPaymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, historyRepository, cartRepository,
                cartService, productClient, paymentClient, new ObjectMapper(), eventPublisher, onlineGateway);
        ReflectionTestUtils.setField(orderService, "shippingFee", new BigDecimal("30000"));
        examInvoiceService = new ExamInvoiceService(orderService, customerInfoLookup);

        when(customerInfoLookup.find(any(), any()))
                .thenReturn(new CustomerInfoLookup.CustomerInfo(null, null));
        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
        when(orderRepository.findByExamPaymentId(any())).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
    }

    // ---------- lập từ sự kiện ----------

    @Test
    void amountSetEvent_createsUnpaidPendingCounterInvoiceForTheCustomer() {
        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(examPaymentId, customerId, new BigDecimal("200000")));

        Order saved = savedOrder();
        assertThat(saved.getChannel()).isEqualTo(OrderChannel.COUNTER);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getPaymentMethod()).isNull();
        assertThat(saved.getUserId()).isEqualTo(customerId);
        assertThat(saved.getExamPaymentId()).isEqualTo(examPaymentId);
        assertThat(saved.getExamAmount()).isEqualByComparingTo("200000");
        assertThat(saved.getSubtotal()).isEqualByComparingTo("0");
        assertThat(saved.getShippingFee()).isEqualByComparingTo("0");
        assertThat(saved.getTotal()).isEqualByComparingTo("200000");
        assertThat(saved.getItems()).isEmpty();
        assertThat(saved.getRecipientName()).isEqualTo("Khách hàng");
        assertThat(saved.getPaidAt()).isNull();
        assertThat(saved.getCompletedAt()).isNull();
        // Chưa động tới kho hay phát sự kiện bán hàng nào.
        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void amountSetEvent_isIdempotent() {
        Order existing = pendingInvoice();
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(existing));

        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(examPaymentId, customerId, new BigDecimal("200000")));

        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void amountSetEvent_lostRaceAgainstStaffOpening_isSwallowed() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new DataIntegrityViolationException("uq"));

        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(examPaymentId, customerId, new BigDecimal("200000")));
    }

    @Test
    void amountSetEvent_withBadData_isIgnoredWithoutError() {
        examInvoiceService.createFromAmountSet(null);
        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(null, customerId, new BigDecimal("1")));
        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(examPaymentId, null, new BigDecimal("1")));
        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(examPaymentId, customerId, null));
        examInvoiceService.createFromAmountSet(new PaymentAmountSetEvent(examPaymentId, customerId, BigDecimal.ZERO));

        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    // ---------- nhân viên mở hoá đơn ----------

    @Test
    void staffOpening_returnsExistingInvoiceWithoutCallingPaymentService() {
        Order existing = pendingInvoice();
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(existing));

        OrderResponse response = examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(paymentClient, never()).getPayment(any(), any());
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void staffOpening_createsMissingInvoiceFromPaymentServiceData() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal("350000"), "PENDING_PAYMENT"));

        OrderResponse response = examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(response.userId()).isEqualTo(customerId);
        assertThat(response.examAmount()).isEqualByComparingTo("350000");
        assertThat(response.total()).isEqualByComparingTo("350000");
    }

    @Test
    void staffOpening_afterLosingRaceToTheEvent_readsTheInvoiceTheEventCreated() {
        Order created = pendingInvoice();
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal("200000"), "PENDING_PAYMENT"));
        when(orderRepository.findByExamPaymentId(examPaymentId))
                .thenReturn(Optional.empty())      // lần 1: chưa có
                .thenReturn(Optional.empty())      // lần 1 (trong createExamInvoiceIfAbsent): vẫn chưa có
                .thenReturn(Optional.of(created));  // lần 2: sự kiện đã tạo xong
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new DataIntegrityViolationException("uq"));

        OrderResponse response = examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        assertThat(response.id()).isEqualTo(created.getId());
        verify(paymentClient, times(1)).getPayment(any(), any());
    }

    @Test
    void staffOpening_rejectsExamThatIsNotWaitingForPayment() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal("200000"), "COMPLETED"));

        assertThatThrownBy(() -> examInvoiceService.getOrCreate(examPaymentId, TOKEN))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("COMPLETED");
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void staffOpening_unknownExam_is404() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenThrow(FeignException.errorStatus("getPayment",
                feign.Response.builder().status(404).reason("nf").headers(Map.of())
                        .request(Request.create(Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null))
                        .build()));

        assertThatThrownBy(() -> examInvoiceService.getOrCreate(examPaymentId, TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- tự điền thông tin khách ----------

    @Test
    void staffOpening_fillsCustomerNameAndPhoneIntoThePendingInvoice() {
        Order existing = pendingInvoice();
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(existing));
        when(orderRepository.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        when(customerInfoLookup.find(customerId, TOKEN))
                .thenReturn(new CustomerInfoLookup.CustomerInfo("Nguyễn Thị Lan", "0901234567"));

        OrderResponse response = examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        assertThat(response.recipientName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(response.recipientPhone()).isEqualTo("0901234567");
        assertThat(existing.getRecipientName()).isEqualTo("Nguyễn Thị Lan");
    }

    @Test
    void staffOpening_doesNotOverwriteWhatStaffAlreadyTyped() {
        Order existing = pendingInvoice();
        existing.setRecipientName("Chị Lan (người nhà)");
        existing.setRecipientPhone("0988000111");
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(existing));

        OrderResponse response = examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        assertThat(response.recipientName()).isEqualTo("Chị Lan (người nhà)");
        assertThat(response.recipientPhone()).isEqualTo("0988000111");
        org.mockito.Mockito.verifyNoInteractions(customerInfoLookup);
    }

    @Test
    void staffOpening_keepsNameTypedByStaffButStillFillsMissingPhone() {
        Order existing = pendingInvoice();
        existing.setRecipientName("Chị Lan (người nhà)");
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(existing));
        when(orderRepository.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        when(customerInfoLookup.find(customerId, TOKEN))
                .thenReturn(new CustomerInfoLookup.CustomerInfo("Nguyễn Thị Lan", "0901234567"));

        OrderResponse response = examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        assertThat(response.recipientName()).isEqualTo("Chị Lan (người nhà)");
        assertThat(response.recipientPhone()).isEqualTo("0901234567");
    }

    @Test
    void staffOpening_whenLookupFindsNothing_orFails_stillReturnsTheInvoice() {
        Order existing = pendingInvoice();
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(existing));

        assertThat(examInvoiceService.getOrCreate(examPaymentId, TOKEN).recipientName()).isEqualTo("Khách hàng");

        when(customerInfoLookup.find(any(), any())).thenThrow(new IllegalStateException("boom"));
        assertThat(examInvoiceService.getOrCreate(examPaymentId, TOKEN).id()).isEqualTo(existing.getId());
    }

    @Test
    void paidInvoice_isLeftAlone() {
        Order paid = pendingInvoice();
        paid.setStatus(OrderStatus.COMPLETED);
        paid.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(paid));

        examInvoiceService.getOrCreate(examPaymentId, TOKEN);

        org.mockito.Mockito.verifyNoInteractions(customerInfoLookup);
    }

    // ---------- luật chặn ----------

    @Test
    void pendingCounterInvoice_cannotBeConfirmedShippedOrCompleted() {
        Order invoice = pendingInvoice();
        when(orderRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> orderService.confirm(invoice.getId(), staffId, null, TOKEN))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> orderService.ship(invoice.getId(), staffId, null))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThatThrownBy(() -> orderService.complete(invoice.getId(), staffId, null))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(productClient, never()).deductStock(any(), any());
    }

    @Test
    void customerCannotCancelPendingCounterInvoice() {
        Order invoice = pendingInvoice();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> orderService.cancel(invoice.getId(), customerId, false, "Đổi ý"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("nhân viên");

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(invoice.getExamPaymentId()).isEqualTo(examPaymentId);
    }

    @Test
    void staffCancel_closesInvoiceReleasesTheExamAndTouchesNoStock() {
        Order invoice = pendingInvoice();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        OrderResponse response = orderService.cancel(invoice.getId(), staffId, true, "Khách không lấy");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.refundRequired()).isFalse();
        // Nhả khoản khám để lập hoá đơn mới được (UNIQUE chỉ cho một hoá đơn mỗi khoản khám).
        assertThat(invoice.getExamPaymentId()).isNull();
        assertThat(invoice.getCancelReason()).isEqualTo("Khách không lấy");
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    void completedCounterInvoice_cannotBeCancelled() {
        Order invoice = pendingInvoice();
        invoice.setStatus(OrderStatus.COMPLETED);
        invoice.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> orderService.cancel(invoice.getId(), staffId, true, "x"))
                .isInstanceOf(InvalidOrderStateException.class);
        assertThat(invoice.getExamPaymentId()).isEqualTo(examPaymentId);
    }

    @Test
    void paymentCompleted_cancelsEmptyPendingInvoice() {
        Order invoice = pendingInvoice();
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(invoice));
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        orderService.cancelPendingExamInvoiceAfterDirectPayment(examPaymentId);

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(invoice.getExamPaymentId()).isNull();
        assertThat(invoice.getCancelReason()).contains("thu tiền mặt trực tiếp");
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    void paymentCompleted_cancelsPendingInvoiceWithProductsAndDoesNotRestock() {
        Order invoice = pendingInvoice();
        invoice.getItems().add(com.vetclinic.order.domain.OrderItem.builder()
                .order(invoice).productId(UUID.randomUUID()).sku("SKU").productName("x")
                .unitPrice(new BigDecimal("1")).quantity(2).lineTotal(new BigDecimal("2")).build());
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(invoice));
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        orderService.cancelPendingExamInvoiceAfterDirectPayment(examPaymentId);

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void paymentCompleted_leavesCollectedInvoiceAlone() {
        Order invoice = pendingInvoice();
        invoice.setStatus(OrderStatus.COMPLETED);
        invoice.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(invoice));
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        orderService.cancelPendingExamInvoiceAfterDirectPayment(examPaymentId);

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(invoice.getExamPaymentId()).isEqualTo(examPaymentId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void paymentCompleted_unknownExam_isIgnored() {
        orderService.cancelPendingExamInvoiceAfterDirectPayment(examPaymentId);
        orderService.cancelPendingExamInvoiceAfterDirectPayment(null);

        verify(orderRepository, never()).findByIdForUpdate(any());
    }

    // ---------- đường thu ngay cũ gặp hoá đơn chờ ----------

    @Test
    void oneClickCounterInvoice_supersedesTheEmptyPendingInvoiceOfTheSameExam() {
        Order placeholder = pendingInvoice();
        when(orderRepository.findByExamPaymentId(examPaymentId)).thenReturn(Optional.of(placeholder));
        when(orderRepository.findByIdForUpdate(placeholder.getId())).thenReturn(Optional.of(placeholder));
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal("200000"), "PENDING_PAYMENT"));

        OrderResponse invoice = orderService.createCounterInvoice(staffId, new CounterInvoiceRequest(
                examPaymentId, null, null, List.of(), PaymentMethod.CASH, null, null), TOKEN);

        assertThat(placeholder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(placeholder.getExamPaymentId()).isNull();
        assertThat(invoice.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(invoice.examPaymentId()).isEqualTo(examPaymentId);
    }

    // ---------- helpers ----------

    private Order savedOrder() {
        org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private Order pendingInvoice() {
        return Order.builder()
                .id(UUID.randomUUID())
                .orderCode("VC-TEST")
                .userId(customerId)
                .status(OrderStatus.PENDING)
                .channel(OrderChannel.COUNTER)
                .paymentStatus(PaymentStatus.UNPAID)
                .recipientName("Khách hàng")
                .recipientPhone("")
                .shippingAddress("Nhận tại quầy")
                .subtotal(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .examPaymentId(examPaymentId)
                .examAmount(new BigDecimal("200000"))
                .total(new BigDecimal("200000"))
                .items(new java.util.ArrayList<>())
                .build();
    }
}
