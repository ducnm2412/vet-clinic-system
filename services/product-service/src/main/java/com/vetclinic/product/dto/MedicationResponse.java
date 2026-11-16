package com.vetclinic.product.dto;

import com.vetclinic.product.domain.Medication;

import java.math.BigDecimal;
import java.util.UUID;

public record MedicationResponse(
        UUID id,
        String name,
        String unit,
        String caution,
        boolean active,
        BigDecimal unitPrice,
        int stockQuantity,
        int lowStockThreshold
) {
    public static MedicationResponse from(Medication m) {
        return new MedicationResponse(m.getId(), m.getName(), m.getUnit(), m.getCaution(), m.isActive(),
                m.getUnitPrice(), m.getStockQuantity(), m.getLowStockThreshold());
    }
}
