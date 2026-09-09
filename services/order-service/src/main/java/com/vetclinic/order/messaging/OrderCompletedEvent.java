package com.vetclinic.order.messaging;

import java.util.List;
import java.util.UUID;

/**
 * CN-37: đơn đã chốt bán (nhân viên xác nhận) — product-service nghe để trừ tồn kho.
 * Cấu trúc phải khớp com.vetclinic.product.messaging.OrderCompletedEvent.
 */
public record OrderCompletedEvent(UUID orderId, List<Line> lines) {

    public record Line(UUID productId, int quantity) {
    }
}
