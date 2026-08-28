package com.vetclinic.booking.dto;

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
