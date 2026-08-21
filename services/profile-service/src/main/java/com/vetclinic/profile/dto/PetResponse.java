package com.vetclinic.profile.dto;

import com.vetclinic.profile.domain.PetGender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PetResponse(
        UUID id,
        String name,
        String species,
        String breed,
        PetGender gender,
        LocalDate dateOfBirth,
        BigDecimal weightKg,
        Instant createdAt,
        Instant updatedAt
) {
}
