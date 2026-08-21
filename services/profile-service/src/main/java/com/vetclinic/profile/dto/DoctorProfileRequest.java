package com.vetclinic.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DoctorProfileRequest(
        @NotBlank @Size(max = 100) String specialty,
        @Size(max = 20) String phone,
        String bio,
        @PositiveOrZero Integer yearsOfExperience
) {
}
