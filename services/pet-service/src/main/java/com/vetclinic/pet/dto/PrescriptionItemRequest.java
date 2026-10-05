package com.vetclinic.pet.dto;

import jakarta.validation.constraints.NotBlank;

public record PrescriptionItemRequest(
        @NotBlank String medicationName,
        @NotBlank String dosage,
        @NotBlank String frequency,
        Integer durationDays,
        String notes
) {
}
