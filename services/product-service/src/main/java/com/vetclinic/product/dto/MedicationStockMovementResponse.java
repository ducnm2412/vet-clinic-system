package com.vetclinic.product.dto;

import com.vetclinic.product.domain.MedicationStockMovement;
import com.vetclinic.product.domain.StockMovementType;

import java.time.Instant;
import java.util.UUID;

public record MedicationStockMovementResponse(
        UUID id,
        UUID medicationId,
        StockMovementType type,
        Integer quantityChange,
        Integer quantityAfter,
        Integer shortfall,
        String note,
        UUID referenceId,
        UUID createdBy,
        Instant createdAt
) {
    public static MedicationStockMovementResponse from(MedicationStockMovement m) {
        return new MedicationStockMovementResponse(m.getId(), m.getMedication().getId(), m.getType(),
                m.getQuantityChange(), m.getQuantityAfter(), m.getShortfall(), m.getNote(), m.getReferenceId(),
                m.getCreatedBy(), m.getCreatedAt());
    }
}
