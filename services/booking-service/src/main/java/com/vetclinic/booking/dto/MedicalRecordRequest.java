package com.vetclinic.booking.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record MedicalRecordRequest(
        @NotBlank String diagnosis,
        String treatment,
        String notes,
        List<PrescriptionItemRequest> prescriptionItems
) {
}
