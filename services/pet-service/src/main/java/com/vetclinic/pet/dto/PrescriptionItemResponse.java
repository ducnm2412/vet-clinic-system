package com.vetclinic.pet.dto;

import java.util.UUID;

public record PrescriptionItemResponse(
        UUID id,
        String medicationName,
        String dosage,
        String frequency,
        Integer durationDays,
        String notes
) {
}
