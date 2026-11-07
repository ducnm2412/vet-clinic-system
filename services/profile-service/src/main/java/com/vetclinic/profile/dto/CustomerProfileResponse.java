package com.vetclinic.profile.dto;

import com.vetclinic.profile.domain.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerProfileResponse(
        UUID id,
        UUID userId,
        String phone,
        LocalDate dateOfBirth,
        Gender gender,
        String photoUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
