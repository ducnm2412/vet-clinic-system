package com.vetclinic.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record DoctorProfileResponse(
        UUID id,
        UUID userId,
        String specialty,
        String phone,
        String bio,
        Integer yearsOfExperience,
        Instant createdAt,
        Instant updatedAt
) {
}
