package com.vetclinic.order.dto;

import com.vetclinic.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Bản rút gọn cho danh sách đơn — không kèm chi tiết dòng hàng. */
public record OrderSummaryResponse(
        UUID id,
        String orderCode,
        OrderStatus status,
        Integer totalItems,
        BigDecimal total,
        String recipientName,
        Instant createdAt
) {
}
