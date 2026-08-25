package com.vetclinic.product.dto;

import com.vetclinic.product.domain.StockMovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID productId,
        StockMovementType type,
        Integer quantityChange,
        Integer quantityAfter,
        String note,
        UUID referenceId,
        UUID createdBy,
        Instant createdAt
) {
}
