package com.vetclinic.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Có đơn hàng đang chờ nhân viên xác nhận (exchange order.events, routing key order.placed).
 * Cấu trúc phải khớp com.vetclinic.order.messaging.OrderPlacedEvent; field lạ bị bỏ qua.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedEvent(UUID orderId, String orderCode, BigDecimal total, int itemCount,
                               String paymentMethod, String recipientName) {
}
