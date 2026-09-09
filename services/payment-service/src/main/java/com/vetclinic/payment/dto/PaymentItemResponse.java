package com.vetclinic.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentItemResponse(
        UUID id,
        String medicationName,
        String dosage,
        String frequency,
        Integer durationDays,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount
) {
}
