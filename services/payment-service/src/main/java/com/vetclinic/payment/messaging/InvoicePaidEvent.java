package com.vetclinic.payment.messaging;

import java.util.UUID;

/**
 * Hoá đơn tại quầy do order-service phát (exchange order.events, routing key order.invoice-paid).
 * Cấu trúc phải khớp com.vetclinic.order.messaging.InvoicePaidEvent; field lạ bị Jackson bỏ qua.
 */
public record InvoicePaidEvent(UUID orderId, UUID examPaymentId, String method) {
}
