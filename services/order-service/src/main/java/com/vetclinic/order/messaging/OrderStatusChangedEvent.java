package com.vetclinic.order.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Đơn đặt qua giỏ hàng vừa đổi trạng thái — notification-service nghe để báo khách (chuông và email).
 * Cấu trúc phải khớp com.vetclinic.notification.messaging.OrderStatusChangedEvent.
 *
 * {@code byCustomer} = chính khách vừa làm việc đó (tự huỷ, bấm "đã nhận hàng"): khách đã biết nên không báo lại.
 * {@code refundNeeded} = đơn huỷ nhưng khách đã trả online, cửa hàng sẽ hoàn tiền.
 */
public record OrderStatusChangedEvent(UUID orderId, String orderCode, UUID customerUserId, String customerEmail,
                                      String recipientName, String status, String paymentMethod,
                                      BigDecimal total, String cancelReason, boolean refundNeeded,
                                      boolean byCustomer) {
}
