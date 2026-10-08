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
import com.vetclinic.order.exception.PaymentServiceUnavailableException;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.exception.StockServiceUnavailableException;
import com.vetclinic.order.messaging.InvoicePaidEvent;
import com.vetclinic.order.messaging.OrderCompletedEvent;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hoá đơn gộp tại quầy: tiền khám (payment-service) + sản phẩm (product-service), thu một lần. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CounterInvoiceTest {

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
        when(onlineGateway.getIfAvailable()).thenReturn(org.mockito.Mockito.mock(OnlinePaymentGateway.class));
        ReflectionTestUtils.setField(orderService, "shippingFee", new BigDecimal("30000"));

        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
        when(orderRepository.existsByExamPaymentId(any())).thenReturn(false);
        // Hibernate gán id lúc persist; mock thì phải tự gán để trừ kho có orderId mà kiểm tra.
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(UUID.randomUUID());
            }
            return o;
        });
        when(productClient.deductStock(any(), any()))
                .thenReturn(new ProductClient.StockDeductionResponse(UUID.randomUUID(), 1));
        when(cartService.fetchProduct(productId)).thenReturn(product(10, true, "150000"));
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(exam("PENDING_PAYMENT", "200000"));
    }

    // ---------- trường hợp chính ----------

    @Test
    void examPlusProducts_oneInvoice_bankTransfer() {
        OrderResponse invoice = orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 2)), PaymentMethod.BANK_TRANSFER, "FT123"), TOKEN);

        assertThat(invoice.channel()).isEqualTo(OrderChannel.COUNTER);
        assertThat(invoice.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(invoice.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(invoice.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(invoice.transferReference()).isEqualTo("FT123");
        assertThat(invoice.paidAt()).isNotNull().isEqualTo(invoice.completedAt());
        // 150000 x 2 = 300000 tiền hàng, cộng 200000 tiền khám, không phí ship tại quầy.
        assertThat(invoice.subtotal()).isEqualByComparingTo("300000");
        assertThat(invoice.examAmount()).isEqualByComparingTo("200000");
        assertThat(invoice.shippingFee()).isEqualByComparingTo("0");
        assertThat(invoice.total()).isEqualByComparingTo("500000");
        // Chủ hoá đơn là chủ khoản khám, không phải người nào request tự khai.
        assertThat(invoice.userId()).isEqualTo(customerId);
        assertThat(invoice.examPaymentId()).isEqualTo(examPaymentId);
        assertThat(invoice.items()).singleElement().satisfies(i -> {
            assertThat(i.unitPrice()).isEqualByComparingTo("150000");
            assertThat(i.quantity()).isEqualTo(2);
        });

        ArgumentCaptor<ProductClient.StockDeductionRequest> deduct =
                ArgumentCaptor.forClass(ProductClient.StockDeductionRequest.class);
        verify(productClient).deductStock(deduct.capture(), eq(TOKEN));
        assertThat(deduct.getValue().orderId()).isEqualTo(invoice.id());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).anySatisfy(e -> assertThat(e).isInstanceOf(OrderCompletedEvent.class));
        assertThat(events.getAllValues()).filteredOn(InvoicePaidEvent.class::isInstance)
                .singleElement().satisfies(e -> {
                    InvoicePaidEvent paid = (InvoicePaidEvent) e;
                    assertThat(paid.orderId()).isEqualTo(invoice.id());
                    assertThat(paid.examPaymentId()).isEqualTo(examPaymentId);
                    assertThat(paid.method()).isEqualTo("BANK_TRANSFER");
                });
    }

    @Test
    void productsOnly_walkInCustomer_noPaymentServiceCall() {
        OrderResponse invoice = orderService.createCounterInvoice(staffId,
                request(null, List.of(line(productId, 1)), PaymentMethod.CASH, null), TOKEN);

        assertThat(invoice.userId()).isNull();
        assertThat(invoice.recipientName()).isEqualTo("Khách lẻ");
        assertThat(invoice.shippingAddress()).isEqualTo("Nhận tại quầy");
        assertThat(invoice.examPaymentId()).isNull();
        assertThat(invoice.examAmount()).isEqualByComparingTo("0");
        assertThat(invoice.total()).isEqualByComparingTo("150000");

        verify(paymentClient, never()).getPayment(any(), any());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).noneMatch(InvoicePaidEvent.class::isInstance);
    }

    @Test
    void examOnly_noStockCallAndNoSaleEvent() {
        OrderResponse invoice = orderService.createCounterInvoice(staffId,
                request(examPaymentId, null, PaymentMethod.CASH, null), TOKEN);

        assertThat(invoice.items()).isEmpty();
        assertThat(invoice.total()).isEqualByComparingTo("200000");
        verify(productClient, never()).deductStock(any(), any());

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(events.capture());
        assertThat(events.getAllValues()).noneMatch(OrderCompletedEvent.class::isInstance);
        assertThat(events.getAllValues()).filteredOn(InvoicePaidEvent.class::isInstance)
                .singleElement().satisfies(e -> assertThat(((InvoicePaidEvent) e).method()).isEqualTo("CASH"));
    }

    @Test
    void cash_dropsTransferReference_andNameTypedByStaffIsKept() {
        CounterInvoiceRequest request = new CounterInvoiceRequest(null, "  Chị Lan ", "0901234567",
                List.of(line(productId, 1)), PaymentMethod.CASH, "KHONG-LIEN-QUAN", null);

        OrderResponse invoice = orderService.createCounterInvoice(staffId, request, TOKEN);

        assertThat(invoice.transferReference()).isNull();
        assertThat(invoice.recipientName()).isEqualTo("Chị Lan");
        assertThat(invoice.recipientPhone()).isEqualTo("0901234567");
    }

    @Test
    void sameProductTwice_isMergedIntoOneLine() {
        OrderResponse invoice = orderService.createCounterInvoice(staffId,
                request(null, List.of(line(productId, 2), line(productId, 3)), PaymentMethod.CASH, null), TOKEN);

        assertThat(invoice.items()).singleElement().satisfies(i -> assertThat(i.quantity()).isEqualTo(5));
        assertThat(invoice.subtotal()).isEqualByComparingTo("750000");
    }

    // ---------- khoản khám không hợp lệ ----------

    @Test
    void examNotAwaitingPayment_isRejected() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(exam("COMPLETED", "200000"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, null, PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNothingSold();
    }

    @Test
    void examWithoutAmountYet_isRejected() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenReturn(
                new PaymentClient.PaymentView(examPaymentId, customerId, null, "PENDING_PAYMENT"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, null, PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("chưa có số tiền");

        verifyNothingSold();
    }

    @Test
    void examAlreadyInAnotherInvoice_isRejectedWithoutCallingPaymentService() {
        when(orderRepository.existsByExamPaymentId(examPaymentId)).thenReturn(true);

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 1)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("đã nằm trong một hoá đơn");

        verify(paymentClient, never()).getPayment(any(), any());
        verifyNothingSold();
    }

    @Test
    void concurrentInvoiceForSameExam_isCaughtByUniqueConstraint() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 1)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(InvalidOrderStateException.class);

        // Chưa trừ kho vì còn nằm trước bước trừ kho.
        verify(productClient, never()).deductStock(any(), any());
    }

    @Test
    void examNotFound_isReportedAsNotFound() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenThrow(feign404());

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, null, PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void paymentServiceDown_refusesInsteadOfGuessingTheAmount() {
        when(paymentClient.getPayment(examPaymentId, TOKEN)).thenThrow(refused("/payment/payments/x"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 1)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(PaymentServiceUnavailableException.class);

        verifyNothingSold();
    }

    // ---------- kho ----------

    @Test
    void notEnoughStock_isRefusedBeforeAnythingIsSaved() {
        when(cartService.fetchProduct(productId)).thenReturn(product(1, true, "150000"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 2)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("chỉ còn 1");

        verifyNothingSold();
    }

    @Test
    void inactiveProduct_isRefused() {
        when(cartService.fetchProduct(productId)).thenReturn(product(10, false, "150000"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(null, List.of(line(productId, 1)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("ngừng bán");

        verifyNothingSold();
    }

    // Kho còn lúc kiểm tra nhưng product-service từ chối lúc trừ (vừa có người khác mua): không được
    // phát bất kỳ sự kiện nào, nên khoản khám không bị hoàn tất nhầm.
    @Test
    void stockRunsOutAtDeduction_publishesNoEvents() {
        when(productClient.deductStock(any(), any())).thenThrow(conflict());

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 2)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(ProductUnavailableException.class)
                .hasMessageContaining("chỉ còn");

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void productServiceDownAtDeduction_publishesNoEvents() {
        when(productClient.deductStock(any(), any())).thenThrow(refused("/products/stock/deduct"));

        assertThatThrownBy(() -> orderService.createCounterInvoice(staffId,
                request(examPaymentId, List.of(line(productId, 2)), PaymentMethod.CASH, null), TOKEN))
                .isInstanceOf(StockServiceUnavailableException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ---------- đồ dùng ----------

    private void verifyNothingSold() {
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(productClient, never()).deductStock(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private CounterInvoiceRequest request(UUID exam, List<CounterInvoiceRequest.Line> items,
                                          PaymentMethod method, String reference) {
        return new CounterInvoiceRequest(exam, null, null, items, method, reference, null);
    }

    private static CounterInvoiceRequest.Line line(UUID productId, int quantity) {
        return new CounterInvoiceRequest.Line(productId, quantity);
    }

    private ProductClient.ProductView product(int stock, boolean active, String price) {
        return new ProductClient.ProductView(productId, "SKU-1", "Hat cho cho", new BigDecimal(price), "tui",
                stock, active);
    }

    private PaymentClient.PaymentView exam(String status, String amount) {
        return new PaymentClient.PaymentView(examPaymentId, customerId, new BigDecimal(amount), status);
    }

    private static Request anyRequest(String path) {
        return Request.create(Request.HttpMethod.GET, path, Map.of(), null, StandardCharsets.UTF_8, null);
    }

    private static FeignException.NotFound feign404() {
        return new FeignException.NotFound("not found", anyRequest("/payment/payments/x"), new byte[0], Map.of());
    }

    private static FeignException.Conflict conflict() {
        String body = "{\"status\":409,\"message\":\"Sản phẩm \\\"Hạt cho chó\\\" chỉ còn 1 túi, cần 2\"}";
        return new FeignException.Conflict("conflict", anyRequest("/products/stock/deduct"),
                body.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    private static RetryableException refused(String path) {
        return new RetryableException(-1, "Connection refused", Request.HttpMethod.GET, (Long) null,
                anyRequest(path));
    }
}
