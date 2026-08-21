package com.vetclinic.profile.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerProfileResponse(
        UUID id,
        UUID userId,
        String phone,
        LocalDate dateOfBirth,
        Instant createdAt,
        Instant updatedAt
) {
}
