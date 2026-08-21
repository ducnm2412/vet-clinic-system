package com.vetclinic.profile.dto;

import com.vetclinic.profile.domain.PetGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PetRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String species,
        @Size(max = 100) String breed,
        PetGender gender,
        @PastOrPresent LocalDate dateOfBirth,
        @Positive BigDecimal weightKg
) {
}
