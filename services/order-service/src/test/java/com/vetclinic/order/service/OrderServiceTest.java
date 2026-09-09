package com.vetclinic.order.service;

import com.vetclinic.order.client.ProductClient;
import com.vetclinic.order.domain.Cart;
import com.vetclinic.order.domain.CartItem;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderItem;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.dto.CheckoutRequest;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.exception.EmptyCartException;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.messaging.OrderCancelledEvent;
import com.vetclinic.order.messaging.OrderCompletedEvent;
import com.vetclinic.order.repository.CartRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartService cartService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    private UUID userId;
    private UUID productId;
    private Cart cart;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, historyRepository, cartRepository,
                cartService, eventPublisher);
        ReflectionTestUtils.setField(orderService, "shippingFee", new BigDecimal("30000"));

        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        cart = Cart.builder().id(UUID.randomUUID()).userId(userId).items(new ArrayList<>()).build();
        cart.getItems().add(CartItem.builder().cart(cart).productId(productId).quantity(2).build());

        when(cartService.getOrCreateCart(userId)).thenReturn(cart);
        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
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

        orderService.confirm(order.getId(), UUID.randomUUID(), "OK");

        ArgumentCaptor<OrderCompletedEvent> captor = ArgumentCaptor.forClass(OrderCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().lines()).hasSize(1);
        assertThat(captor.getValue().lines().get(0).quantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirm_alreadyConfirmed_throws() {
        Order order = existingOrder(OrderStatus.CONFIRMED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(order.getId(), UUID.randomUUID(), null))
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
