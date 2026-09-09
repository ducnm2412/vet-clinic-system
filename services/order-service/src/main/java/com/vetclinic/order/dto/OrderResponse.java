package com.vetclinic.order.dto;

import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderCode,
        UUID userId,
        OrderStatus status,
        PaymentMethod paymentMethod,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        String note,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal total,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant confirmedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancelReason
) {
}
