package com.vetclinic.pet.dto;

import com.vetclinic.pet.domain.Medication;

import java.math.BigDecimal;
import java.util.UUID;

public record MedicationResponse(
        UUID id,
        String name,
        String unit,
        String caution,
        boolean active,
        BigDecimal unitPrice
) {
    public static MedicationResponse from(Medication m) {
        return new MedicationResponse(m.getId(), m.getName(), m.getUnit(), m.getCaution(), m.isActive(),
                m.getUnitPrice());
    }
}
