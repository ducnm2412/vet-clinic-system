package com.vetclinic.order.dto;

import com.vetclinic.order.domain.OrderChannel;
import com.vetclinic.order.domain.OrderStatus;
import com.vetclinic.order.domain.PaymentMethod;
import com.vetclinic.order.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderCode,
        UUID userId,
        OrderStatus status,
        OrderChannel channel,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        String note,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        UUID examPaymentId,
        BigDecimal examAmount,
        BigDecimal total,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant confirmedAt,
        Instant completedAt,
        Instant paidAt,
        Instant paymentExpiresAt,
        String transferReference,
        Instant cancelledAt,
        String cancelReason,
        // Đơn online đã trả tiền nhưng bị huỷ: nhân viên cần hoàn tiền thủ công cho khách.
        boolean refundRequired
) {
}
