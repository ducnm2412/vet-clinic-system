package com.vetclinic.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Đơn đặt qua giỏ hàng vừa đổi trạng thái (exchange order.events, routing key order.status-changed).
 * Cấu trúc phải khớp com.vetclinic.order.messaging.OrderStatusChangedEvent; field lạ bị bỏ qua.
 *
 * {@code byCustomer}: chính khách vừa làm (tự huỷ, bấm "đã nhận hàng") nên không báo lại.
 * {@code refundNeeded}: đơn huỷ nhưng khách đã trả online.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStatusChangedEvent(UUID orderId, String orderCode, UUID customerUserId, String customerEmail,
                                      String recipientName, String status, String paymentMethod,
                                      BigDecimal total, String cancelReason, boolean refundNeeded,
                                      boolean byCustomer) {
}
