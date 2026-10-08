package com.vetclinic.order.service;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.OrderStatusHistory;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;
import com.vetclinic.order.dto.MockPaymentSubmitRequest;
import com.vetclinic.order.dto.PaymentInitResponse;
import com.vetclinic.order.dto.PaymentResultResponse;
import com.vetclinic.order.dto.PaymentResultResponse.Outcome;
import com.vetclinic.order.exception.InvalidOrderStateException;
import com.vetclinic.order.exception.ResourceNotFoundException;
import com.vetclinic.order.payment.GatewayResult;
import com.vetclinic.order.payment.GatewayUnavailableException;
import com.vetclinic.order.payment.MockPaymentGateway;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/** Thanh toán online cho đơn đặt qua giỏ hàng: cấp link trả tiền và ghi nhận kết quả cổng báo về. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ObjectProvider<OnlinePaymentGateway> gatewayProvider;
    private final ObjectProvider<MockPaymentGateway> mockGatewayProvider;

    /**
     * Cấp link trả tiền cho đơn của chính khách. Mỗi lần gọi cấp mã giao dịch mới và vô hiệu mã cũ,
     * nên link cũ (đã mở ở tab khác, đã gửi cho người khác) không dùng để trả được nữa.
     */
    @Transactional
    public PaymentInitResponse startPayment(UUID userId, UUID orderId, String clientIp) {
        OnlinePaymentGateway gateway = requireGateway();

        Order order = orderRepository.findById(orderId)
                .filter(o -> userId.equals(o.getUserId()))
                // 404 thay vì 403: người lạ không dò được id đơn nào có thật.
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));

        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new InvalidOrderStateException("Đơn này không thanh toán online");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidOrderStateException("Đơn này đã được thanh toán");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Đơn đã " + (order.getStatus() == OrderStatus.CANCELLED
                    ? "bị huỷ" : "được xử lý") + ", không thể thanh toán");
        }
        if (isExpired(order)) {
            throw new InvalidOrderStateException("Đơn đã quá hạn thanh toán");
        }

        // Chỉ chữ và số: cổng thật (VNPAY) yêu cầu mã giao dịch dạng alphanumeric.
        String txnRef = order.getOrderCode().replace("-", "")
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setGatewayTxnRef(txnRef);
        // Cắt về giây: cổng ghi thời điểm này theo giây, lưu lẻ hơn thì hỏi lại sẽ lệch.
        order.setGatewayTxnCreatedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        orderRepository.save(order);

        return new PaymentInitResponse(order.getId(), gateway.buildPaymentUrl(order, txnRef, clientIp),
                order.getPaymentExpiresAt());
    }

    /**
     * Ghi nhận kết quả cổng báo về. Gọi lại nhiều lần với cùng kết quả không gây thêm tác dụng.
     * Chữ ký sai thì ném InvalidGatewaySignatureException; các trường hợp còn lại luôn trả kết quả
     * (cổng cần nhận phản hồi thành công để thôi gọi lại).
     */
    @Transactional
    public PaymentResultResponse handleCallback(Map<String, String> params) {
        return recordGatewayResult(requireGateway().verifyCallback(params));
    }

    /**
     * Ghi nhận một kết quả đã được tin cậy (qua kiểm chữ ký, hoặc do chính ta hỏi cổng). Dùng chung cho thông
     * báo cổng gửi về và cho việc hỏi lại trạng thái trước khi huỷ đơn quá hạn.
     */
    @Transactional
    public PaymentResultResponse recordGatewayResult(GatewayResult result) {
        Order order = orderRepository.findByGatewayTxnRefForUpdate(result.txnRef()).orElse(null);
        if (order == null) {
            log.warn("Cổng báo giao dịch {} nhưng không có đơn nào mang mã này", result.txnRef());
            return new PaymentResultResponse(Outcome.UNKNOWN_TRANSACTION, null, null, null);
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return respond(Outcome.ALREADY_PAID, order);
        }

        if (!result.success()) {
            // Đơn vẫn chờ thanh toán, khách vào lại và trả tiếp được.
            if (order.getStatus() == OrderStatus.PENDING) {
                recordNote(order, "Thanh toán online không thành công, đơn vẫn chờ thanh toán");
            }
            return respond(Outcome.FAILED, order);
        }

        if (order.getTotal().setScale(0, RoundingMode.HALF_UP).compareTo(result.amount()) != 0) {
            log.error("Đơn {}: cổng báo trả {} nhưng tổng đơn là {} — không ghi nhận, cần kiểm tra giao dịch {}",
                    order.getOrderCode(), result.amount(), order.getTotal(), result.txnRef());
            return respond(Outcome.AMOUNT_MISMATCH, order);
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            // Khách trả đúng lúc đơn vừa bị huỷ (hết hạn hoặc tự huỷ). Tiền đã vào nên không thể bỏ qua:
            // đơn nằm ở "huỷ nhưng đã trả" để nhân viên hoàn tiền thủ công.
            log.warn("Đơn {} đã huỷ nhưng cổng báo đã trả (giao dịch {}) — cần hoàn tiền thủ công",
                    order.getOrderCode(), result.gatewayTransactionNo());
            recordNote(order, "Nhận được tiền sau khi đơn đã bị huỷ, cần hoàn tiền cho khách (giao dịch "
                    + result.gatewayTransactionNo() + ")");
        } else {
            log.info("Đơn {} đã thanh toán online (giao dịch {})", order.getOrderCode(),
                    result.gatewayTransactionNo());
            recordNote(order, "Khách đã thanh toán online (giao dịch " + result.gatewayTransactionNo() + ")");
        }
        return respond(Outcome.PAID, order);
    }

    /**
     * Cổng giả lập: trang giả lập gửi lựa chọn của khách. Ở đây đóng vai cổng — kiểm tra link chưa
     * bị sửa, dựng kết quả đã ký rồi xử lý qua đúng đường mà cổng thật sẽ gọi vào ({@link #handleCallback}).
     */
    @Transactional
    public PaymentResultResponse submitMockPayment(UUID userId, MockPaymentSubmitRequest request) {
        MockPaymentGateway mock = mockGatewayProvider.getIfAvailable();
        if (mock == null) {
            throw new GatewayUnavailableException();
        }

        mock.verifyPaymentRequest(request.txnRef(), request.orderCode(), request.amount(), request.signature());

        Order order = orderRepository.findByGatewayTxnRef(request.txnRef())
                .filter(o -> userId.equals(o.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch thanh toán"));
        log.debug("Cổng giả lập xử lý giao dịch của đơn {}", order.getOrderCode());

        return handleCallback(mock.buildCallback(request.txnRef(), request.amount(), request.outcome()));
    }

    private OnlinePaymentGateway requireGateway() {
        OnlinePaymentGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new GatewayUnavailableException();
        }
        return gateway;
    }

    private static boolean isExpired(Order order) {
        return order.getPaymentExpiresAt() != null && Instant.now().isAfter(order.getPaymentExpiresAt());
    }

    /** Ghi một dòng vào lịch sử đơn mà không đổi trạng thái (from = to). */
    private void recordNote(Order order, String note) {
        historyRepository.save(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(order.getStatus())
                .toStatus(order.getStatus())
                .changedBy(null)
                .note(note)
                .build());
    }

    private static PaymentResultResponse respond(Outcome outcome, Order order) {
        return new PaymentResultResponse(outcome, order.getId(), order.getOrderCode(), order.getPaymentStatus());
    }
}
