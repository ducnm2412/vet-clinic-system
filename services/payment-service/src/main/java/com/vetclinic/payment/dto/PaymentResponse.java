package com.vetclinic.payment.dto;

import com.vetclinic.payment.domain.PaymentMethod;
import com.vetclinic.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID medicalRecordId,
        UUID appointmentId,
        UUID customerUserId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        Instant paidAt,
        List<PaymentItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
