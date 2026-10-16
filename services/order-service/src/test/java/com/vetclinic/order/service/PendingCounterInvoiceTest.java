package com.vetclinic.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.client.PaymentClient;
import com.vetclinic.order.client.ProductClient;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.domain.OrderItem;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.CollectCounterInvoiceRequest;
import com.vetclinic.order.dto.CounterInvoiceRequest;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.messaging.InvoicePaidEvent;
import com.vetclinic.order.messaging.OrderCompletedEvent;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.repository.CartRepository;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import feign.FeignException;
import feign.Request;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sửa dòng hàng rồi thu tiền trên hoá đơn quầy đang chờ (Bước 3). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingCounterInvoiceTest {

    private static final String TOKEN = "Bearer staff-token";

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartService cartService;
    @Mock private ProductClient productClient;
    @Mock private PaymentClient paymentClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ObjectProvider<OnlinePaymentGateway> onlineGateway;

    private OrderService orderService;

    private final UUID staffId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID examPaymentId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, historyRepository, cartRepository,
                cartService, productClient, paymentClient, new ObjectMapper(), eventPublisher, onlineGateway);
        ReflectionTestUtils.setField(orderService, "shippingFee", new BigDecimal("30000"));
        when(productClient.deductStock(any(), any()))
                .thenReturn(new ProductClient.StockDeductionResponse(UUID.randomUUID(), 1));
        when(cartService.fetchProduct(productId)).thenReturn(product(10, true, "150000"));
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal("200000"), "PENDING_PAYMENT"));
    }

    @Test
    void replaceItems_snapshotsPriceWithoutDeductingStock() {
        Order invoice = pendingInvoice();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        OrderResponse updated = orderService.replaceCounterItems(invoice.getId(),
                List.of(new CounterInvoiceRequest.Line(productId, 2)));

        assertThat(updated.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(updated.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(updated.subtotal()).isEqualByComparingTo("300000");
        assertThat(updated.examAmount()).isEqualByComparingTo("200000");
        assertThat(updated.total()).isEqualByComparingTo("500000");
        assertThat(updated.items()).singleElement().satisfies(i -> {
            assertThat(i.unitPrice()).isEqualByComparingTo("150000");
            assertThat(i.quantity()).isEqualTo(2);
        });
        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void replaceItems_emptyList_keepsExamOnlyTotal() {
        Order invoice = pendingInvoice();
        invoice.getItems().add(OrderItem.builder().order(invoice).productId(productId)
                .sku("SKU-1").productName("x").unitPrice(new BigDecimal("150000"))
                .quantity(1).lineTotal(new BigDecimal("150000")).build());
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        OrderResponse updated = orderService.replaceCounterItems(invoice.getId(), List.of());

        assertThat(updated.items()).isEmpty();
        assertThat(updated.subtotal()).isEqualByComparingTo("0");
        assertThat(updated.total()).isEqualByComparingTo("200000");
    }

    @Test
    void replaceItems_rejectsOnlineOrders() {
        Order online = pendingInvoice();
        online.setChannel(OrderChannel.ONLINE);
        when(orderRepository.findByIdForUpdate(online.getId())).thenReturn(Optional.of(online));

        assertThatThrownBy(() -> orderService.replaceCounterItems(online.getId(), List.of()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void replaceItems_rejectsWhenNotEnoughStock() {
        Order invoice = pendingInvoice();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(cartService.fetchProduct(productId)).thenReturn(product(1, true, "150000"));

        assertThatThrownBy(() -> orderService.replaceCounterItems(invoice.getId(),
                List.of(new CounterInvoiceRequest.Line(productId, 2))))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("chỉ còn 1");
        assertThat(invoice.getItems()).isEmpty();
    }

    @Test
    void collect_examPlusProducts_deductsStockAndPaysTheExam() {
        Order invoice = pendingInvoiceWithProduct();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        OrderResponse paid = orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.BANK_TRANSFER, "Chị Lan", "0901234567", "FT9"), TOKEN);

        assertThat(paid.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(paid.transferReference()).isEqualTo("FT9");
        assertThat(paid.recipientName()).isEqualTo("Chị Lan");
        assertThat(paid.recipientPhone()).isEqualTo("0901234567");
        assertThat(paid.paidAt()).isNotNull().isEqualTo(paid.completedAt());
        assertThat(paid.total()).isEqualByComparingTo("350000");

        verify(productClient).deductStock(any(), eq(TOKEN));
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).anySatisfy(e -> assertThat(e).isInstanceOf(OrderCompletedEvent.class));
        assertThat(events.getAllValues()).filteredOn(InvoicePaidEvent.class::isInstance)
                .singleElement().satisfies(e -> {
                    InvoicePaidEvent paidExam = (InvoicePaidEvent) e;
                    assertThat(paidExam.examPaymentId()).isEqualTo(examPaymentId);
                    assertThat(paidExam.method()).isEqualTo("BANK_TRANSFER");
                });
    }

    @Test
    void collect_examOnly_doesNotTouchStock() {
        Order invoice = pendingInvoice();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        OrderResponse paid = orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.CASH, null, null, "IGNORE"), TOKEN);

        assertThat(paid.paymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(paid.transferReference()).isNull();
        assertThat(paid.items()).isEmpty();
        verify(productClient, never()).deductStock(any(), any());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).noneMatch(OrderCompletedEvent.class::isInstance);
        assertThat(events.getAllValues()).anySatisfy(e -> assertThat(e).isInstanceOf(InvoicePaidEvent.class));
    }

    @Test
    void collect_whenPriceChanged_refusesWithoutCharging() {
        Order invoice = pendingInvoiceWithProduct();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(cartService.fetchProduct(productId)).thenReturn(product(10, true, "180000"));

        assertThatThrownBy(() -> orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.CASH, null, null, null), TOKEN))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("đã đổi");

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collect_whenStockRanOut_refusesWithoutCharging() {
        Order invoice = pendingInvoiceWithProduct();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(cartService.fetchProduct(productId)).thenReturn(product(0, true, "150000"));

        assertThatThrownBy(() -> orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.CASH, null, null, null), TOKEN))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("chỉ còn 0");

        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collect_whenDeductionFails_publishesNoEvents() {
        Order invoice = pendingInvoiceWithProduct();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(productClient.deductStock(any(), any())).thenThrow(conflict());

        assertThatThrownBy(() -> orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.CASH, null, null, null), TOKEN))
                .isInstanceOf(ProductUnavailableException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collect_whenExamWasAlreadyPaidElsewhere_refusesWithoutCharging() {
        Order invoice = pendingInvoiceWithProduct();
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal("200000"), "COMPLETED"));

        assertThatThrownBy(() -> orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.CASH, null, null, null), TOKEN))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("COMPLETED");

        assertThat(invoice.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collect_rejectsAlreadyPaidInvoice() {
        Order invoice = pendingInvoice();
        invoice.setStatus(OrderStatus.COMPLETED);
        invoice.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> orderService.collectCounterInvoice(invoice.getId(), staffId,
                collect(PaymentMethod.CASH, null, null, null), TOKEN))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    private Order pendingInvoice() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("VC-WAIT")
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
                .items(new ArrayList<>())
                .build();
        return order;
    }

    private Order pendingInvoiceWithProduct() {
        Order invoice = pendingInvoice();
        invoice.getItems().add(OrderItem.builder()
                .order(invoice)
                .productId(productId)
                .sku("SKU-1")
                .productName("Hat cho cho")
                .unitPrice(new BigDecimal("150000"))
                .quantity(1)
                .lineTotal(new BigDecimal("150000"))
                .build());
        invoice.setSubtotal(new BigDecimal("150000"));
        invoice.setTotal(new BigDecimal("350000"));
        return invoice;
    }

    private static CollectCounterInvoiceRequest collect(PaymentMethod method, String name, String phone, String ref) {
        return new CollectCounterInvoiceRequest(method, name, phone, ref, null);
    }

    private ProductClient.ProductView product(int stock, boolean active, String price) {
        return new ProductClient.ProductView(productId, "SKU-1", "Hat cho cho", new BigDecimal(price), "tui",
                stock, active);
    }

    private static FeignException.Conflict conflict() {
        String body = "{\"status\":409,\"message\":\"Sản phẩm \\\"Hạt cho chó\\\" chỉ còn 1 túi, cần 2\"}";
        return new FeignException.Conflict("conflict",
                Request.create(Request.HttpMethod.POST, "/products/stock/deduct", Map.of(), null,
                        StandardCharsets.UTF_8, null),
                body.getBytes(StandardCharsets.UTF_8), Map.of());
    }
}
