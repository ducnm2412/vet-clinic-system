package com.vetclinic.order.dto;

import com.vetclinic.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusHistoryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        UUID changedBy,
        String note,
        Instant createdAt
) {
}
