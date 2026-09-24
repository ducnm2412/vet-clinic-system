package com.vetclinic.order.service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetclinic.order.client.ProductClient;
import com.vetclinic.order.domain.Cart;
import com.vetclinic.order.domain.CartItem;
import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderItem;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.OrderStatusHistory;
import com.vetclinic.order.dto.CheckoutRequest;
import com.vetclinic.order.dto.OrderItemResponse;
import com.vetclinic.order.dto.OrderResponse;
import com.vetclinic.order.dto.OrderStatusHistoryResponse;
import com.vetclinic.order.dto.OrderSummaryResponse;
import com.vetclinic.order.dto.PageResponse;
import com.vetclinic.order.exception.EmptyCartException;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ProductUnavailableException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.exception.StockServiceUnavailableException;
import com.vetclinic.order.messaging.OrderCancelledEvent;
import com.vetclinic.order.messaging.OrderCompletedEvent;
import com.vetclinic.order.repository.CartRepository;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    /** Chỉ dùng để đọc câu giải thích trong thân lỗi 409 của product-service. */
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${order.shipping-fee}")
    private BigDecimal shippingFee;

    // ---------- CN-33: đặt hàng ----------

    @Transactional
    public OrderResponse checkout(UUID userId, CheckoutRequest request) {
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
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId);
        }

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getHistory(UUID orderId, UUID requesterId, boolean isStaff) {
        Order order = findOrThrow(orderId);
        if (!isStaff && !order.getUserId().equals(requesterId)) {
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
        order.setCompletedAt(Instant.now());
        return toResponse(order);
    }

    /**
     * Huỷ đơn. Khách chỉ huỷ được đơn của mình và chỉ khi còn PENDING; nhân viên huỷ được
     * cả đơn đã CONFIRMED, khi đó phải hoàn hàng về kho.
     */
    @Transactional
    public OrderResponse cancel(UUID orderId, UUID requesterId, boolean isStaff, String reason) {
        Order order = findOrThrow(orderId);

        if (!isStaff) {
            if (!order.getUserId().equals(requesterId)) {
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

        if (needRestock) {
            List<OrderCancelledEvent.Line> lines = order.getItems().stream()
                    .map(i -> new OrderCancelledEvent.Line(i.getProductId(), i.getQuantity()))
                    .toList();
            eventPublisher.publishEvent(new OrderCancelledEvent(order.getId(), lines));
            log.info("Đơn {} huỷ sau khi đã trừ kho — phát sự kiện hoàn kho", order.getOrderCode());
        }

        return toResponse(order);
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
                o.getPaymentMethod(), o.getRecipientName(), o.getRecipientPhone(), o.getShippingAddress(),
                o.getNote(), o.getSubtotal(), o.getShippingFee(), o.getTotal(), items,
                o.getCreatedAt(), o.getConfirmedAt(), o.getCompletedAt(), o.getCancelledAt(), o.getCancelReason());
    }

    private OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(o.getId(), o.getOrderCode(), o.getStatus(),
                o.getItems().size(), o.getTotal(), o.getRecipientName(), o.getCreatedAt());
    }
}
