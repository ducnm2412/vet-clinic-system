package com.vetclinic.order.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.vetclinic.order.dto.CounterInvoiceRequest;
import com.vetclinic.order.dto.OrderItemResponse;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.dto.OrderStatusHistoryResponse;
import com.vetclinic.order.dto.OrderSummaryResponse;
import com.vetclinic.order.dto.PageResponse;
import com.vetclinic.order.exception.EmptyCartException;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.PaymentServiceUnavailableException;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.exception.StockServiceUnavailableException;
import com.vetclinic.order.messaging.InvoicePaidEvent;
import com.vetclinic.order.messaging.OrderCancelledEvent;
import com.vetclinic.order.messaging.OrderCompletedEvent;
import com.vetclinic.order.payment.GatewayUnavailableException;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.repository.CartRepository;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** CN-33, CN-35, CN-36, CN-37: đặt hàng và xử lý đơn. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    /** Chỉ dùng để đọc câu giải thích trong thân lỗi 409 của product-service. */
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    /** Rỗng khi hệ thống chưa bật cổng thanh toán online nào. */
    private final ObjectProvider<OnlinePaymentGateway> onlineGateway;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${order.shipping-fee}")
    private BigDecimal shippingFee;

    // Đơn ONLINE phải trả trong khoảng này kể từ lúc đặt, quá hạn thì bị tự huỷ.
    @Value("${order.online-payment-timeout-minutes}")
    private long onlinePaymentTimeoutMinutes;

    // ---------- CN-33: đặt hàng ----------

    @Transactional
    public OrderResponse checkout(UUID userId, CheckoutRequest request) {
        // Không cho đặt đơn online khi chưa có cổng để trả: đơn sẽ kẹt chờ thanh toán rồi tự huỷ.
        if (request.paymentMethod() == PaymentMethod.ONLINE && onlineGateway.getIfAvailable() == null) {
            throw new GatewayUnavailableException();
        }

        Cart cart = cartService.getOrCreateCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .userId(userId)
                .status(OrderStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .shippingAddress(request.shippingAddress())
                .note(request.note())
                .shippingFee(shippingFee)
                .build();
        if (request.paymentMethod() == PaymentMethod.ONLINE) {
            // Đơn ONLINE ở PENDING/UNPAID cho tới khi cổng báo đã trả; hết hạn mà chưa trả thì tự huỷ.
            order.setPaymentExpiresAt(Instant.now().plus(Duration.ofMinutes(onlinePaymentTimeoutMinutes)));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            // Giá và tồn kho lấy từ product-service tại đúng thời điểm này, không tin
            // bất cứ con số nào client gửi lên.
            ProductClient.ProductView p = cartService.fetchProduct(cartItem.getProductId());

            if (Boolean.FALSE.equals(p.active())) {
                throw new ProductUnavailableException("Sản phẩm đã ngừng bán: " + p.name());
            }
            if (p.stockQuantity() < cartItem.getQuantity()) {
                throw new ProductUnavailableException(
                        "Sản phẩm \"" + p.name() + "\" chỉ còn " + p.stockQuantity() + " " + p.unit()
                                + ", không đủ " + cartItem.getQuantity());
            }

            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            items.add(OrderItem.builder()
                    .order(order)
                    .productId(p.id())
                    .sku(p.sku())
                    .productName(p.name())
                    .unitPrice(p.price())
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        order.setItems(items);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal.add(shippingFee));

        orderRepository.saveAndFlush(order);
        recordHistory(order, null, OrderStatus.PENDING, userId, "Khách đặt hàng");

        // Đơn đã tạo xong thì giỏ phải rỗng, tránh khách bấm đặt hai lần.
        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Đã tạo đơn {} cho user {} — {} dòng hàng, tổng {}",
                order.getOrderCode(), userId, items.size(), order.getTotal());

        return toResponse(order);
    }

    // ---------- Hoá đơn gộp tại quầy ----------

    /**
     * Nhân viên lập và thu hoá đơn tại quầy trong một lần: tiền khám/thuốc (nếu có) cộng các sản
     * phẩm khách mua, thu bằng tiền mặt hoặc chuyển khoản. Không có trạng thái "đã lập, chưa thu":
     * hoá đơn ra đời là COMPLETED và PAID, nên không phải lo huỷ hay hoàn kho cho hoá đơn dở dang.
     *
     * Mọi con số lấy từ server: giá và tồn kho từ product-service, số tiền khám và chủ khoản khám từ
     * payment-service. Request chỉ chọn "khoản nào, sản phẩm nào, bao nhiêu".
     *
     * Trừ kho đặt SAU CÙNG (sau khi đơn đã flush xuống DB): trừ kho là bước duy nhất không rollback
     * được, nên mọi lỗi dữ liệu phải lộ ra trước nó. Còn lại một khe rất hẹp là commit hỏng sau khi
     * product-service đã trừ, giống rủi ro đã chấp nhận ở confirm().
     */
    @Transactional
    public OrderResponse createCounterInvoice(UUID staffId, CounterInvoiceRequest request, String bearerToken) {
        PaymentClient.PaymentView payment = request.examPaymentId() == null
                ? null
                : loadPayableExam(request.examPaymentId(), bearerToken);

        BigDecimal examAmount = payment == null ? BigDecimal.ZERO : payment.amount();
        UUID customerUserId = payment == null ? null : payment.customerUserId();

        Instant now = Instant.now();
        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .userId(customerUserId)
                .status(OrderStatus.COMPLETED)
                .channel(OrderChannel.COUNTER)
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.PAID)
                .recipientName(counterCustomerName(request.customerName(), customerUserId))
                .recipientPhone(request.customerPhone() == null ? "" : request.customerPhone())
                .shippingAddress("Nhận tại quầy")
                .note(request.note())
                .shippingFee(BigDecimal.ZERO)
                .examPaymentId(request.examPaymentId())
                .examAmount(examAmount)
                // Mã giao dịch chỉ có nghĩa với chuyển khoản; tiền mặt thì bỏ.
                .transferReference(request.paymentMethod() == PaymentMethod.BANK_TRANSFER
                        ? blankToNull(request.transferReference()) : null)
                .confirmedAt(now)
                .completedAt(now)
                .paidAt(now)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<UUID, Integer> line : mergeLines(request.items()).entrySet()) {
            ProductClient.ProductView p = cartService.fetchProduct(line.getKey());
            int quantity = line.getValue();

            if (Boolean.FALSE.equals(p.active())) {
                throw new ProductUnavailableException("Sản phẩm đã ngừng bán: " + p.name());
            }
            if (p.stockQuantity() < quantity) {
                throw new ProductUnavailableException(
                        "Sản phẩm \"" + p.name() + "\" chỉ còn " + p.stockQuantity() + " " + p.unit()
                                + ", không đủ " + quantity);
            }

            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineTotal);
            items.add(OrderItem.builder()
                    .order(order)
                    .productId(p.id())
                    .sku(p.sku())
                    .productName(p.name())
                    .unitPrice(p.price())
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .build());
        }

        order.setItems(items);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal.add(examAmount));

        try {
            orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Hai nhân viên cùng lập hoá đơn cho một khoản khám: ràng buộc UNIQUE chặn người đến sau.
            throw new InvalidOrderStateException("Khoản khám này đã nằm trong một hoá đơn khác");
        }

        recordHistory(order, null, OrderStatus.CONFIRMED, staffId, "Lập hoá đơn tại quầy");
        recordHistory(order, OrderStatus.CONFIRMED, OrderStatus.COMPLETED, staffId,
                "Đã thu tiền: " + request.paymentMethod());
        orderRepository.flush();

        if (!items.isEmpty()) {
            deductStock(order, bearerToken);
            eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), items.stream()
                    .map(i -> new OrderCompletedEvent.Line(i.getProductId(), i.getQuantity()))
                    .toList()));
        }
        if (payment != null) {
            eventPublisher.publishEvent(new InvoicePaidEvent(
                    order.getId(), request.examPaymentId(), request.paymentMethod().name()));
        }

        log.info("Hoá đơn tại quầy {}: {} dòng hàng, tiền khám {}, tổng {}, thu bằng {}",
                order.getOrderCode(), items.size(), examAmount, order.getTotal(), request.paymentMethod());

        return toResponse(order);
    }

    /** Khoản khám phải có thật, đã có số tiền, đang chờ thu và chưa nằm trong hoá đơn nào. */
    private PaymentClient.PaymentView loadPayableExam(UUID examPaymentId, String bearerToken) {
        if (orderRepository.existsByExamPaymentId(examPaymentId)) {
            throw new InvalidOrderStateException("Khoản khám này đã nằm trong một hoá đơn khác");
        }

        PaymentClient.PaymentView payment;
        try {
            payment = paymentClient.getPayment(examPaymentId, bearerToken);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Không tìm thấy khoản khám: " + examPaymentId);
        } catch (FeignException e) {
            throw new PaymentServiceUnavailableException(e);
        }

        if (!"PENDING_PAYMENT".equals(payment.status())) {
            throw new InvalidOrderStateException(
                    "Khoản khám không ở trạng thái chờ thu tiền (hiện là " + payment.status() + ")");
        }
        if (payment.amount() == null || payment.amount().signum() <= 0) {
            throw new InvalidOrderStateException("Khoản khám chưa có số tiền cần thu");
        }
        return payment;
    }

    /** Gộp các dòng cùng sản phẩm thành một dòng, giữ thứ tự nhân viên nhập. */
    private static Map<UUID, Integer> mergeLines(List<CounterInvoiceRequest.Line> lines) {
        Map<UUID, Integer> merged = new LinkedHashMap<>();
        if (lines != null) {
            for (CounterInvoiceRequest.Line line : lines) {
                merged.merge(line.productId(), line.quantity(), Integer::sum);
            }
        }
        return merged;
    }

    private static String counterCustomerName(String typedName, UUID customerUserId) {
        String name = blankToNull(typedName);
        if (name != null) {
            return name;
        }
        return customerUserId == null ? "Khách lẻ" : "Khách hàng";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    // ---------- CN-35: khách theo dõi đơn ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMyOrders(UUID userId, Pageable pageable) {
        Page<OrderSummaryResponse> page = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toSummary);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(UUID userId, UUID orderId) {
        Order order = findOrThrow(orderId);
        // Không dùng 403 ở đây: trả 404 để người lạ không dò được id đơn nào có thật.
        // userId của đơn có thể NULL (hoá đơn tại quầy của khách lẻ) nên so từ phía người gọi.
        if (!userId.equals(order.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId);
        }

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getHistory(UUID orderId, UUID requesterId, boolean isStaff) {
        Order order = findOrThrow(orderId);
        if (!isStaff && !requesterId.equals(order.getUserId())) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId);
        }

        return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(h -> new OrderStatusHistoryResponse(h.getFromStatus(), h.getToStatus(),
                        h.getChangedBy(), h.getNote(), h.getCreatedAt()))
                .toList();
    }

    // ---------- CN-36: nhân viên xử lý đơn ----------

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listAllOrders(OrderStatus status, Pageable pageable) {
        return PageResponse.from(orderRepository.findAllByStatus(status, pageable).map(this::toSummary));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderAsStaff(UUID orderId) {
        return toResponse(findOrThrow(orderId));
    }

    /**
     * CN-37: xác nhận đơn — đây là lúc chốt bán nên trừ kho.
     *
     * VD-14: trừ kho ĐỒNG BỘ, đợi product-service trả lời rồi mới chốt. Trước đây chỉ phát sự
     * kiện rồi trả về ngay: thiếu hàng thì bên kia ghi log và bỏ qua, đơn vẫn CONFIRMED trong
     * khi kho không trừ — tức là hứa bán món không còn hàng.
     *
     * Gọi remote nằm TRONG transaction, chấp nhận có lúc product-service đã trừ xong mà
     * order-service commit hỏng. Khi đó đơn vẫn chờ xác nhận và nhân viên bấm lại: lần gọi sau
     * mang cùng orderId nên product-service nhận ra đã trừ rồi và không trừ lần hai.
     */
    @Transactional
    public OrderResponse confirm(UUID orderId, UUID staffId, String note, String bearerToken) {
        // Đơn online phải có tiền rồi mới chốt bán và trừ kho, kẻo kho bị giữ cho đơn không ai trả.
        Order pending = findOrThrow(orderId);
        if (pending.getStatus() == OrderStatus.PENDING && pending.getPaymentMethod() == PaymentMethod.ONLINE
                && pending.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidOrderStateException(
                    "Đơn thanh toán online chưa được thanh toán, chưa thể xác nhận");
        }

        Order order = transition(orderId, OrderStatus.CONFIRMED, staffId, note);
        order.setConfirmedAt(Instant.now());

        deductStock(order, bearerToken);

        List<OrderCompletedEvent.Line> lines = order.getItems().stream()
                .map(i -> new OrderCompletedEvent.Line(i.getProductId(), i.getQuantity()))
                .toList();

        // Sự kiện vẫn phát để service khác biết đơn đã chốt bán. product-service nghe và bỏ qua
        // vì đã trừ ở trên rồi (chống trùng theo orderId).
        eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), lines));

        return toResponse(order);
    }

    private void deductStock(Order order, String bearerToken) {
        List<ProductClient.StockDeductionRequest.Line> lines = order.getItems().stream()
                .map(i -> new ProductClient.StockDeductionRequest.Line(i.getProductId(), i.getQuantity()))
                .toList();

        try {
            var result = productClient.deductStock(
                    new ProductClient.StockDeductionRequest(order.getId(), lines), bearerToken);
            log.info("Đơn {}: đã trừ kho {} dòng hàng", order.getOrderCode(), result.applied());
        } catch (FeignException.Conflict e) {
            // Thiếu hàng: product-service không trừ dòng nào. Trả nguyên văn lời giải thích của
            // bên kia (có tên sản phẩm và số còn lại) cho nhân viên đang đứng ở quầy.
            throw new ProductUnavailableException(messageOf(e));
        } catch (FeignException.NotFound e) {
            throw new ProductUnavailableException("Có sản phẩm trong đơn không còn tồn tại");
        } catch (FeignException e) {
            // Chỉ bắt lỗi GỌI service (tắt máy, quá hạn, 5xx). Lỗi lập trình phải nổi lên thành
            // 500 để còn thấy mà sửa, không nguỵ trang thành "thử lại sau".
            throw new StockServiceUnavailableException(e);
        }
    }

    /** Lấy trường "message" trong thân lỗi của product-service; không đọc được thì nói chung chung. */
    private String messageOf(FeignException e) {
        try {
            JsonNode message = objectMapper.readTree(e.contentUTF8()).get("message");
            if (message != null && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (JacksonException ignored) {
            // Thân lỗi không phải JSON quen thuộc — dùng câu mặc định bên dưới.
        }
        return "Kho không đủ hàng cho đơn này";
    }

    @Transactional
    public OrderResponse ship(UUID orderId, UUID staffId, String note) {
        return toResponse(transition(orderId, OrderStatus.SHIPPING, staffId, note));
    }

    @Transactional
    public OrderResponse complete(UUID orderId, UUID staffId, String note) {
        Order order = transition(orderId, OrderStatus.COMPLETED, staffId, note);
        Instant now = Instant.now();
        order.setCompletedAt(now);
        // COD: giao xong là đã thu tiền. Đơn online đã trả từ trước, giữ nguyên thời điểm trả thật.
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(now);
        }
        return toResponse(order);
    }

    /**
     * Huỷ đơn. Khách chỉ huỷ được đơn của mình và chỉ khi còn PENDING; nhân viên huỷ được
     * cả đơn đã CONFIRMED, khi đó phải hoàn hàng về kho.
     */
    @Transactional
    public OrderResponse cancel(UUID orderId, UUID requesterId, boolean isStaff, String reason) {
        // Khoá dòng: cổng có thể đang báo "đã trả" cho đúng đơn này, huỷ không được ghi đè kết quả đó.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        if (!isStaff) {
            if (!requesterId.equals(order.getUserId())) {
                throw new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId);
            }
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new InvalidOrderStateException(
                        "Đơn đã được xác nhận, vui lòng liên hệ nhân viên để huỷ");
            }
        }

        boolean needRestock = order.getStatus().stockAlreadyDeducted();

        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStateException(
                    "Không thể huỷ đơn đang ở trạng thái " + from);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelReason(reason);
        recordHistory(order, from, OrderStatus.CANCELLED, requesterId, reason);
        if (isRefundRequired(order)) {
            log.warn("Đơn {} đã thanh toán online nhưng bị huỷ — cần hoàn tiền thủ công", order.getOrderCode());
            recordHistory(order, OrderStatus.CANCELLED, OrderStatus.CANCELLED, requesterId,
                    "Đơn đã thanh toán online, cần hoàn tiền thủ công cho khách");
        }

        if (needRestock) {
            List<OrderCancelledEvent.Line> lines = order.getItems().stream()
                    .map(i -> new OrderCancelledEvent.Line(i.getProductId(), i.getQuantity()))
                    .toList();
            eventPublisher.publishEvent(new OrderCancelledEvent(order.getId(), lines));
            log.info("Đơn {} huỷ sau khi đã trừ kho — phát sự kiện hoàn kho", order.getOrderCode());
        }

        return toResponse(order);
    }

    /** Đơn online còn chờ khách trả mà đã quá hạn. Chỉ đọc: để job hỏi cổng trước khi quyết định huỷ. */
    @Transactional(readOnly = true)
    public Optional<Order> findExpiredUnpaidOnlineOrder(UUID orderId) {
        return orderRepository.findById(orderId).filter(OrderService::isExpiredUnpaidOnline);
    }

    private static boolean isExpiredUnpaidOnline(Order order) {
        return order.getPaymentMethod() == PaymentMethod.ONLINE
                && order.getPaymentStatus() == PaymentStatus.UNPAID
                && order.getStatus() == OrderStatus.PENDING
                && order.getPaymentExpiresAt() != null
                && !order.getPaymentExpiresAt().isAfter(Instant.now());
    }

    /**
     * Huỷ một đơn online quá hạn mà vẫn chưa trả. Gọi từ job quét định kỳ, mỗi đơn một transaction riêng.
     * Xét lại điều kiện sau khi khoá dòng: khách có thể vừa trả hoặc vừa tự huỷ giữa lúc job chọn đơn
     * và lúc xử lý. Đơn PENDING chưa trừ kho nên không có gì để hoàn.
     *
     * @return true nếu đơn vừa bị huỷ, false nếu không còn thuộc diện quá hạn
     */
    @Transactional
    public boolean cancelExpiredUnpaidOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !isExpiredUnpaidOnline(order)) {
            return false;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelReason("Quá hạn thanh toán online");
        recordHistory(order, OrderStatus.PENDING, OrderStatus.CANCELLED, null,
                "Tự huỷ do quá hạn thanh toán online");
        log.info("Đơn {} quá hạn thanh toán online, đã tự huỷ", order.getOrderCode());
        return true;
    }

    // ---------- nội bộ ----------

    private Order transition(UUID orderId, OrderStatus target, UUID actorId, String note) {
        Order order = findOrThrow(orderId);
        OrderStatus from = order.getStatus();

        if (!from.canTransitionTo(target)) {
            throw new InvalidOrderStateException(
                    "Không thể chuyển đơn từ " + from + " sang " + target);
        }

        order.setStatus(target);
        recordHistory(order, from, target, actorId, note);

        return order;
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to, UUID actorId, String note) {
        historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(actorId)
                .note(note)
                .build());
    }

    /**
     * Mã đơn dạng VC240825-A1B2 cho khách đọc qua điện thoại. Phần ngẫu nhiên dùng
     * SecureRandom và bỏ các ký tự dễ đọc nhầm (0/O, 1/I).
     */
    private String generateOrderCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder suffix = new StringBuilder(4);
            for (int i = 0; i < 4; i++) {
                suffix.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
            }

            String code = "VC" + LocalDate.now().format(CODE_DATE) + "-" + suffix;
            if (!orderRepository.existsByOrderCode(code)) {
                return code;
            }
        }

        // 5 lần trùng liên tiếp gần như không xảy ra; rơi vào đây thì dùng UUID cho chắc.
        return "VC" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProductId(), i.getSku(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
                .toList();

        return new OrderResponse(o.getId(), o.getOrderCode(), o.getUserId(), o.getStatus(),
                o.getChannel(), o.getPaymentMethod(), o.getPaymentStatus(),
                o.getRecipientName(), o.getRecipientPhone(), o.getShippingAddress(),
                o.getNote(), o.getSubtotal(), o.getShippingFee(), o.getExamPaymentId(), o.getExamAmount(),
                o.getTotal(), items,
                o.getCreatedAt(), o.getConfirmedAt(), o.getCompletedAt(), o.getPaidAt(), o.getPaymentExpiresAt(), o.getTransferReference(),
                o.getCancelledAt(), o.getCancelReason(), isRefundRequired(o));
    }

    private OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(o.getId(), o.getOrderCode(), o.getStatus(),
                o.getChannel(), o.getPaymentMethod(), o.getPaymentStatus(),
                o.getItems().size(), o.getTotal(), o.getRecipientName(), o.getCreatedAt(), isRefundRequired(o));
    }

    private static boolean isRefundRequired(Order o) {
        return o.getStatus() == OrderStatus.CANCELLED && o.getPaymentStatus() == PaymentStatus.PAID;
    }
}
