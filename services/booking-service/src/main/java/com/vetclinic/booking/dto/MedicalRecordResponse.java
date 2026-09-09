package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.PrescriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MedicalRecordResponse(
        UUID id,
        UUID appointmentId,
        String diagnosis,
        String treatment,
        String notes,
        PrescriptionStatus status,
        List<PrescriptionItemResponse> prescriptionItems,
        Instant createdAt,
        Instant updatedAt
) {
}
