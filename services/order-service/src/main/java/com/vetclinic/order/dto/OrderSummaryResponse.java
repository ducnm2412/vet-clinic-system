package com.vetclinic.order.dto;

import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Bản rút gọn cho danh sách đơn — không kèm chi tiết dòng hàng. */
public record OrderSummaryResponse(
        UUID id,
        String orderCode,
        OrderStatus status,
        OrderChannel channel,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Integer totalItems,
        BigDecimal total,
        String recipientName,
        Instant createdAt,
        boolean refundRequired
) {
}
