package com.vetclinic.order.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Có đơn đặt qua giỏ hàng đang chờ nhân viên xác nhận — notification-service nghe để báo nhân viên quầy.
 *
 * Phát đúng lúc nhân viên làm được việc: đơn COD ngay khi khách đặt; đơn online khi tiền đã về
 * (đơn online chưa trả thì chưa xác nhận được nên chưa có gì để báo).
 * Cấu trúc phải khớp com.vetclinic.notification.messaging.OrderPlacedEvent.
 */
public record OrderPlacedEvent(UUID orderId, String orderCode, BigDecimal total, int itemCount,
                               String paymentMethod, String recipientName) {
}
