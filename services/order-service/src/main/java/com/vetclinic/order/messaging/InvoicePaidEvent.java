package com.vetclinic.order.messaging;

import java.util.UUID;

/**
 * Hoá đơn tại quầy đã thu tiền và có gộp khoản khám — payment-service nghe để chuyển khoản đó
 * sang COMPLETED (rồi pet-service nhận payment.completed như bình thường).
 *
 * method là tên enum dạng chuỗi (CASH, BANK_TRANSFER), khớp PaymentMethod của payment-service.
 */
public record InvoicePaidEvent(UUID orderId, UUID examPaymentId, String method) {
}
