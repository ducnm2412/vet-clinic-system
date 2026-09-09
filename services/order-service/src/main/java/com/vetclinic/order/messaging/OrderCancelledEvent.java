package com.vetclinic.order.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Đơn bị huỷ SAU KHI đã trừ kho — product-service nghe để hoàn hàng về kho.
 * Đơn còn PENDING thì chưa trừ kho nên không phát sự kiện này.
 */
public record OrderCancelledEvent(UUID orderId, List<Line> lines) {

    public record Line(UUID productId, int quantity) {
    }
}
