package com.vetclinic.product.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Đơn hàng bị huỷ sau khi đã trừ kho — hoàn hàng về kho.
 * Đơn còn chờ xác nhận thì chưa trừ kho nên order-service không phát sự kiện này.
 */
public record OrderCancelledEvent(UUID orderId, List<Line> lines) {

    public record Line(UUID productId, int quantity) {
    }
}
