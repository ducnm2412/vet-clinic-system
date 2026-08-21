package com.vetclinic.profile.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DoctorLicenseResponse(
        UUID id,
        String licenseNumber,
        String issuedBy,
        LocalDate issuedDate,
        LocalDate expiryDate,
        Instant createdAt,
        Instant updatedAt
) {
}
