package com.vetclinic.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.client.PaymentClient;
import com.vetclinic.order.client.ProductClient;
import com.vetclinic.order.domain.Cart;
import com.vetclinic.order.domain.CartItem;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.domain.OrderItem;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.OrderStatusHistory;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.CheckoutRequest;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.exception.EmptyCartException;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.exception.StockServiceUnavailableException;
import com.vetclinic.order.messaging.OrderCancelledEvent;
import com.vetclinic.order.messaging.OrderCompletedEvent;
import com.vetclinic.order.payment.GatewayUnavailableException;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.repository.CartRepository;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartService cartService;
    @Mock private ProductClient productClient;
    @Mock private PaymentClient paymentClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ObjectProvider<OnlinePaymentGateway> onlineGateway;

    private OrderService orderService;

    private static final String TOKEN = "Bearer staff-token";

    private UUID userId;
    private UUID productId;
    private Cart cart;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, historyRepository, cartRepository,
                cartService, productClient, paymentClient, new ObjectMapper(), eventPublisher, onlineGateway);
        when(onlineGateway.getIfAvailable()).thenReturn(org.mockito.Mockito.mock(OnlinePaymentGateway.class));
        ReflectionTestUtils.setField(orderService, "shippingFee", new BigDecimal("30000"));
        ReflectionTestUtils.setField(orderService, "onlinePaymentTimeoutMinutes", 30L);

        // Huỷ/hết hạn đọc đơn bằng bản khoá dòng; test dùng chung kho đơn giả với findById.
        when(orderRepository.findByIdForUpdate(any())).thenAnswer(inv -> orderRepository.findById(inv.getArgument(0)));

        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        cart = Cart.builder().id(UUID.randomUUID()).userId(userId).items(new ArrayList<>()).build();
        cart.getItems().add(CartItem.builder().cart(cart).productId(productId).quantity(2).build());

        when(cartService.getOrCreateCart(userId)).thenReturn(cart);
        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
        // Mặc định product-service trừ kho thành công; test nào cần hỏng thì stub đè lên.
        when(productClient.deductStock(any(), any()))
                .thenReturn(new ProductClient.StockDeductionResponse(UUID.randomUUID(), 1));
    }

    private void stubProduct(int stock, boolean active, String price) {
        when(cartService.fetchProduct(productId)).thenReturn(new ProductClient.ProductView(
                productId, "SKU-1", "Hat cho cho", new BigDecimal(price), "tui", stock, active));
    }

    private CheckoutRequest request() {
        return new CheckoutRequest("Nguyen Van A", "0901234567", "12 Le Loi", null, PaymentMethod.COD);
    }

    // ---------- CN-33: checkout ----------

    @Test
    void checkout_computesTotalsAndSnapshotsPrice() {
        stubProduct(10, true, "150000");

        OrderResponse response = orderService.checkout(userId, request());

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.subtotal()).isEqualByComparingTo("300000");   // 150000 x 2
        assertThat(response.shippingFee()).isEqualByComparingTo("30000");
        assertThat(response.total()).isEqualByComparingTo("330000");
        assertThat(response.items()).hasSize(1);
        // Giá phải được chụp lại vào đơn, không phải tham chiếu sang product-service.
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("150000");
        assertThat(response.items().get(0).sku()).isEqualTo("SKU-1");
    }

    @Test
    void checkout_cod_hasNoPaymentDeadline() {
        stubProduct(10, true, "150000");

        OrderResponse response = orderService.checkout(userId, request());

        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(response.paymentExpiresAt()).isNull();
    }

    @Test
    void checkout_online_isPendingUnpaidWithDeadline() {
        stubProduct(10, true, "150000");
        Instant before = Instant.now();

        OrderResponse response = orderService.checkout(userId, new CheckoutRequest(
                "Nguyen Van A", "0901234567", "12 Le Loi", null, PaymentMethod.ONLINE));

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(response.paidAt()).isNull();
        // Hạn trả = lúc đặt + 30 phút (cho phép lệch vài giây do thời gian chạy test).
        assertThat(response.paymentExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofMinutes(30)));
        assertThat(response.total()).isEqualByComparingTo("330000");
    }

    @Test
    void checkout_online_stillDoesNotDeductStock() {
        stubProduct(10, true, "150000");

        orderService.checkout(userId, new CheckoutRequest(
                "Nguyen Van A", "0901234567", "12 Le Loi", null, PaymentMethod.ONLINE));

        // Kho chỉ trừ khi nhân viên xác nhận đơn (sau khi đã trả tiền), không phải lúc đặt.
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkout_online_withoutGateway_isRejectedBeforeAnythingIsSaved() {
        when(onlineGateway.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> orderService.checkout(userId, new CheckoutRequest(
                "Nguyen Van A", "0901234567", "12 Le Loi", null, PaymentMethod.ONLINE)))
                .isInstanceOf(GatewayUnavailableException.class);

        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkout_cod_doesNotNeedGateway() {
        when(onlineGateway.getIfAvailable()).thenReturn(null);
        stubProduct(10, true, "150000");

        assertThat(orderService.checkout(userId, request()).paymentMethod()).isEqualTo(PaymentMethod.COD);
    }

    @Test
    void checkout_generatesReadableOrderCode() {
        stubProduct(10, true, "150000");

        String code = orderService.checkout(userId, request()).orderCode();

        // Dạng VCyyMMdd-XXXX để khách đọc qua điện thoại.
        assertThat(code).matches("VC\\d{6}-[A-Z2-9]{4}");
    }

    @Test
    void checkout_clearsCart() {
        stubProduct(10, true, "150000");

        orderService.checkout(userId, request());

        // Giỏ phải rỗng sau khi đặt, tránh khách bấm đặt hai lần.
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void checkout_emptyCart_throws() {
        cart.getItems().clear();

        assertThatThrownBy(() -> orderService.checkout(userId, request()))
                .isInstanceOf(EmptyCartException.class);
    }

    @Test
    void checkout_insufficientStock_throws() {
        stubProduct(1, true, "150000");   // khách đặt 2, kho còn 1

        assertThatThrownBy(() -> orderService.checkout(userId, request()))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("chỉ còn 1");
    }

    @Test
    void checkout_inactiveProduct_throws() {
        stubProduct(10, false, "150000");

        assertThatThrownBy(() -> orderService.checkout(userId, request()))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("ngừng bán");
    }

    @Test
    void checkout_doesNotPublishStockEvent() {
        stubProduct(10, true, "150000");

        orderService.checkout(userId, request());

        // Đặt hàng chưa trừ kho — chỉ xác nhận mới trừ.
        verify(eventPublisher, never()).publishEvent(any(OrderCompletedEvent.class));
    }

    // ---------- CN-37: xác nhận đơn phát sự kiện trừ kho ----------

    @Test
    void confirm_publishesOrderCompletedEvent() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.confirm(order.getId(), UUID.randomUUID(), "OK", TOKEN);

        ArgumentCaptor<OrderCompletedEvent> captor = ArgumentCaptor.forClass(OrderCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().lines()).hasSize(1);
        assertThat(captor.getValue().lines().get(0).quantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirm_deductsStockSynchronouslyWithStaffToken() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.confirm(order.getId(), UUID.randomUUID(), "OK", TOKEN);

        ArgumentCaptor<ProductClient.StockDeductionRequest> captor =
                ArgumentCaptor.forClass(ProductClient.StockDeductionRequest.class);
        verify(productClient).deductStock(captor.capture(), eq(TOKEN));
        assertThat(captor.getValue().orderId()).isEqualTo(order.getId());
        assertThat(captor.getValue().lines()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(2));
    }

    // VD-14: thiếu hàng thì đơn KHÔNG được xác nhận, và không phát sự kiện nào.
    @Test
    void confirm_whenStockRunsOut_keepsOrderPendingAndTellsStaffWhichItem() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(productClient.deductStock(any(), any())).thenThrow(conflict(
                "{\"timestamp\":\"2026-09-24T10:00:00Z\",\"status\":409,"
                        + "\"message\":\"Sản phẩm \\\"Hạt cho chó\\\" chỉ còn 1 túi, cần 2\"}"));

        assertThatThrownBy(() -> orderService.confirm(order.getId(), UUID.randomUUID(), null, TOKEN))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("chỉ còn 1 túi");

        verify(eventPublisher, never()).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    void confirm_whenProductServiceIsDown_refusesInsteadOfSellingBlind() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(productClient.deductStock(any(), any())).thenThrow(new RetryableException(
                -1, "Connection refused", Request.HttpMethod.POST, (Long) null,
                Request.create(Request.HttpMethod.POST, "/products/stock/deduct", Map.of(), null, StandardCharsets.UTF_8, null)));

        assertThatThrownBy(() -> orderService.confirm(order.getId(), UUID.randomUUID(), null, TOKEN))
                .isInstanceOf(StockServiceUnavailableException.class);

        verify(eventPublisher, never()).publishEvent(any(OrderCompletedEvent.class));
    }

    private static FeignException.Conflict conflict(String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/products/stock/deduct", Map.of(), null,
                StandardCharsets.UTF_8, null);
        return new FeignException.Conflict("conflict", request, body.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    @Test
    void confirm_alreadyConfirmed_throws() {
        Order order = existingOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(order.getId(), UUID.randomUUID(), null, TOKEN))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void ship_fromPending_throws() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // Không được bỏ qua bước xác nhận.
        assertThatThrownBy(() -> orderService.ship(order.getId(), UUID.randomUUID(), null))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ---------- Huỷ đơn ----------

    @Test
    void cancelPendingOrder_doesNotRestock() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.cancel(order.getId(), userId, false, "Đổi ý");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // Chưa từng trừ kho thì không phát sự kiện hoàn kho.
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    void cancelConfirmedOrder_publishesRestockEvent() {
        Order order = existingOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        orderService.cancel(order.getId(), UUID.randomUUID(), true, "Hết hàng thực tế");

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().lines().get(0).quantity()).isEqualTo(2);
        assertThat(order.getCancelReason()).isEqualTo("Hết hàng thực tế");
    }

    @Test
    void customerCannotCancelConfirmedOrder() {
        Order order = existingOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(order.getId(), userId, false, "Đổi ý"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("liên hệ nhân viên");
    }

    @Test
    void customerCannotSeeOthersOrder() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // Trả 404 chứ không phải 403, để người lạ không dò được id đơn nào có thật.
        assertThatThrownBy(() -> orderService.getMyOrder(UUID.randomUUID(), order.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void counterOrderWithoutAccount_isInvisibleToCustomersInsteadOfCrashing() {
        // Hoá đơn của khách lẻ có userId NULL: không được NPE, chỉ trả 404 như đơn của người khác.
        Order order = existingOrder(OrderStatus.COMPLETED);
        order.setUserId(null);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getMyOrder(userId, order.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.getHistory(order.getId(), userId, false))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> orderService.cancel(order.getId(), userId, false, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void complete_codOrderBecomesPaid() {
        Order order = existingOrder(OrderStatus.SHIPPING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getChannel()).isEqualTo(OrderChannel.ONLINE);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.complete(order.getId(), UUID.randomUUID(), null);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isNotNull().isEqualTo(response.completedAt());
    }

    // ---------- đơn thanh toán online: xác nhận, giao, huỷ, hết hạn ----------

    private Order onlineOrder(OrderStatus status, PaymentStatus paymentStatus) {
        Order order = existingOrder(status);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setPaymentStatus(paymentStatus);
        order.setPaymentExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        if (paymentStatus == PaymentStatus.PAID) {
            order.setPaidAt(Instant.now().minus(Duration.ofMinutes(5)));
        }
        return order;
    }

    @Test
    void confirm_onlineOrderNotPaidYet_isRefusedAndStockUntouched() {
        Order order = onlineOrder(OrderStatus.PENDING, PaymentStatus.UNPAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(order.getId(), UUID.randomUUID(), null, TOKEN))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("chưa được thanh toán");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any(OrderCompletedEvent.class));
    }

    @Test
    void confirm_onlineOrderAlreadyPaid_goesThrough() {
        Order order = onlineOrder(OrderStatus.PENDING, PaymentStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.confirm(order.getId(), UUID.randomUUID(), null, TOKEN);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(productClient).deductStock(any(), eq(TOKEN));
    }

    @Test
    void confirm_codOrder_stillConfirmsWithoutPayment() {
        Order order = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(orderService.confirm(order.getId(), UUID.randomUUID(), null, TOKEN).status())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void complete_onlineOrder_keepsOriginalPaidAt() {
        Order order = onlineOrder(OrderStatus.SHIPPING, PaymentStatus.PAID);
        Instant paidAt = order.getPaidAt();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.complete(order.getId(), UUID.randomUUID(), null);

        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isEqualTo(paidAt).isBefore(response.completedAt());
    }

    @Test
    void cancel_paidOnlineOrder_isFlaggedForManualRefund() {
        Order order = onlineOrder(OrderStatus.PENDING, PaymentStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(order.getId(), userId, false, "Đổi ý");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.refundRequired()).isTrue();
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues().get(1).getNote()).contains("hoàn tiền");
    }

    @Test
    void cancel_unpaidOrders_doNotNeedRefund() {
        Order online = onlineOrder(OrderStatus.PENDING, PaymentStatus.UNPAID);
        Order cod = existingOrder(OrderStatus.PENDING);
        when(orderRepository.findById(online.getId())).thenReturn(Optional.of(online));
        when(orderRepository.findById(cod.getId())).thenReturn(Optional.of(cod));

        assertThat(orderService.cancel(online.getId(), userId, false, "x").refundRequired()).isFalse();
        assertThat(orderService.cancel(cod.getId(), userId, false, "x").refundRequired()).isFalse();
    }

    @Test
    void expiredUnpaidOnlineOrder_isCancelledWithoutRestock() {
        Order order = onlineOrder(OrderStatus.PENDING, PaymentStatus.UNPAID);
        order.setPaymentExpiresAt(Instant.now().minusSeconds(5));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThat(orderService.cancelExpiredUnpaidOrder(order.getId())).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).contains("Quá hạn");
        assertThat(order.getCancelledAt()).isNotNull();
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
        verify(historyRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    void expiredCheck_skipsOrdersThatNoLongerQualify() {
        Order paid = onlineOrder(OrderStatus.PENDING, PaymentStatus.PAID);
        paid.setPaymentExpiresAt(Instant.now().minusSeconds(5));
        Order notYetDue = onlineOrder(OrderStatus.PENDING, PaymentStatus.UNPAID);
        Order alreadyCancelled = onlineOrder(OrderStatus.CANCELLED, PaymentStatus.UNPAID);
        alreadyCancelled.setPaymentExpiresAt(Instant.now().minusSeconds(5));
        Order cod = existingOrder(OrderStatus.PENDING);
        for (Order o : java.util.List.of(paid, notYetDue, alreadyCancelled, cod)) {
            when(orderRepository.findById(o.getId())).thenReturn(Optional.of(o));
            assertThat(orderService.cancelExpiredUnpaidOrder(o.getId())).isFalse();
        }

        // Khách vừa trả đúng lúc job chạy: đơn phải còn nguyên, không bị huỷ.
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(notYetDue.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderService.cancelExpiredUnpaidOrder(UUID.randomUUID())).isFalse();
        verify(historyRepository, never()).save(any(OrderStatusHistory.class));
    }

    @Test
    void findExpiredUnpaidOnlineOrder_returnsOnlyOrdersStillWaitingPastTheirDeadline() {
        Order expired = onlineOrder(OrderStatus.PENDING, PaymentStatus.UNPAID);
        expired.setPaymentExpiresAt(Instant.now().minusSeconds(5));
        Order notDue = onlineOrder(OrderStatus.PENDING, PaymentStatus.UNPAID);
        Order paid = onlineOrder(OrderStatus.PENDING, PaymentStatus.PAID);
        paid.setPaymentExpiresAt(Instant.now().minusSeconds(5));
        for (Order o : java.util.List.of(expired, notDue, paid)) {
            when(orderRepository.findById(o.getId())).thenReturn(Optional.of(o));
        }

        assertThat(orderService.findExpiredUnpaidOnlineOrder(expired.getId())).containsSame(expired);
        assertThat(orderService.findExpiredUnpaidOnlineOrder(notDue.getId())).isEmpty();
        assertThat(orderService.findExpiredUnpaidOnlineOrder(paid.getId())).isEmpty();
        assertThat(orderService.findExpiredUnpaidOnlineOrder(UUID.randomUUID())).isEmpty();
    }

    private Order existingOrder(OrderStatus status) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("VC260825-AB12")
                .userId(userId)
                .status(status)
                .paymentMethod(PaymentMethod.COD)
                .recipientName("Nguyen Van A")
                .recipientPhone("0901234567")
                .shippingAddress("12 Le Loi")
                .subtotal(new BigDecimal("300000"))
                .shippingFee(new BigDecimal("30000"))
                .total(new BigDecimal("330000"))
                .items(new ArrayList<>())
                .build();

        order.getItems().add(OrderItem.builder()
                .order(order).productId(productId).sku("SKU-1").productName("Hat cho cho")
                .unitPrice(new BigDecimal("150000")).quantity(2).lineTotal(new BigDecimal("300000"))
                .build());

        return order;
    }

    @Test
    void listOrderItems_areIndependentSnapshots() {
        // Đơn giữ tên/giá riêng, nên sản phẩm bị xoá khỏi product-service vẫn đọc được đơn cũ.
        Order order = existingOrder(OrderStatus.COMPLETED);
        List<OrderItem> items = order.getItems();

        assertThat(items.get(0).getProductName()).isEqualTo("Hat cho cho");
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("150000");
    }
}
