package com.vetclinic.order.payment;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Chỉ một cổng thanh toán online được bật. Bật hai cổng cùng lúc (ví dụ quên tắt cổng giả lập khi chuyển
 * sang VNPAY) thì khách có thể bị dẫn sang cổng nào cũng được — dừng ngay lúc khởi động cho chắc.
 */
@Component
public class OnlineGatewayExclusivityCheck {

    public OnlineGatewayExclusivityCheck(List<OnlinePaymentGateway> gateways) {
        if (gateways.size() > 1) {
            throw new IllegalStateException("Chỉ được bật một cổng thanh toán online, đang bật: "
                    + gateways.stream().map(g -> g.getClass().getSimpleName()).toList()
                    + ". Tắt order.mock-gateway.enabled hoặc order.vnpay.enabled.");
        }
    }
}
