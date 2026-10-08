package com.vetclinic.order.service;

import com.vetclinic.order.domain.Order;
import com.vetclinic.order.dto.PaymentResultResponse.Outcome;
import com.vetclinic.order.payment.GatewayQueryException;
import com.vetclinic.order.payment.GatewayResult;
import com.vetclinic.order.payment.OnlinePaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Xử lý một đơn online đã quá hạn thanh toán: hỏi cổng xem khách có thật sự chưa trả rồi mới huỷ.
 *
 * Thông báo của cổng (IPN) có thể bị lỡ — máy chủ tắt đúng lúc, mạng đứt, quá số lần cổng thử lại. Nếu chỉ dựa
 * vào "chưa nhận được thông báo" để huỷ thì khách đã trả tiền vẫn bị huỷ đơn và phải hoàn tiền thủ công.
 *
 * Cố ý KHÔNG đánh dấu @Transactional: lời gọi mạng tới cổng không được nằm trong transaction đang giữ khoá dòng.
 * Từng bước đọc, ghi nhận, huỷ tự có transaction riêng ở các service bên dưới.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiredOnlineOrderProcessor {

    /**
     * Cổng không trả lời được lâu hơn mức này sau hạn thì thôi chờ và huỷ. Đơn online chưa trả không giữ kho nên
     * chờ không hại gì, nhưng không để đơn treo mãi nếu cổng hỏng dài ngày. Nếu sau đó tiền vẫn về, đơn nằm ở
     * trạng thái "huỷ nhưng đã trả" để hoàn tiền.
     */
    static final Duration GATEWAY_PATIENCE = Duration.ofHours(24);

    private final OrderService orderService;
    private final OnlinePaymentService onlinePaymentService;
    private final ObjectProvider<OnlinePaymentGateway> gatewayProvider;

    /** @return true nếu đơn vừa bị huỷ */
    public boolean process(UUID orderId) {
        Optional<Order> candidate = orderService.findExpiredUnpaidOnlineOrder(orderId);
        if (candidate.isEmpty()) {
            return false;   // khách vừa trả, vừa tự huỷ, hoặc đơn không còn thuộc diện quá hạn
        }
        Order order = candidate.get();

        OnlinePaymentGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway != null && order.getGatewayTxnRef() != null) {
            try {
                Optional<GatewayResult> remote = gateway.queryTransaction(order);
                if (remote.isPresent() && remote.get().success()) {
                    Outcome outcome = onlinePaymentService.recordGatewayResult(remote.get()).outcome();
                    if (outcome == Outcome.PAID || outcome == Outcome.ALREADY_PAID) {
                        log.warn("Đơn {} quá hạn nhưng cổng cho biết khách ĐÃ trả (thông báo bị lỡ) — ghi nhận đã thanh toán, không huỷ",
                                order.getOrderCode());
                        return false;
                    }
                    // Cổng báo trả nhưng không ghi nhận được (ví dụ sai số tiền): đã có log lỗi ở bước ghi nhận,
                    // đơn vẫn chưa được tính là đã trả nên đi tiếp tới huỷ.
                }
            } catch (GatewayQueryException e) {
                boolean waitedLongEnough = order.getPaymentExpiresAt() != null
                        && order.getPaymentExpiresAt().isBefore(Instant.now().minus(GATEWAY_PATIENCE));
                if (!waitedLongEnough) {
                    // Chưa biết khách đã trả hay chưa: giữ nguyên, lượt quét sau hỏi lại.
                    log.warn("Chưa hỏi được cổng về đơn quá hạn {} ({}), thử lại ở lượt sau",
                            order.getOrderCode(), e.getMessage());
                    return false;
                }
                log.error("Cổng không trả lời hơn {} giờ cho đơn {}, huỷ đơn quá hạn", GATEWAY_PATIENCE.toHours(),
                        order.getOrderCode(), e);
            }
        }
        return orderService.cancelExpiredUnpaidOrder(orderId);
    }
}
