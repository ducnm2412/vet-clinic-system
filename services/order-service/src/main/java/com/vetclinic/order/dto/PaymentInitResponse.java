package com.vetclinic.order.dto;

import java.time.Instant;
import java.util.UUID;

/** Link để khách sang cổng trả tiền cho đơn, kèm hạn trả để giao diện đếm ngược. */
public record PaymentInitResponse(UUID orderId, String paymentUrl, Instant expiresAt) {
}
