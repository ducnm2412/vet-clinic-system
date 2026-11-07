package com.vetclinic.profile.dto;

import com.vetclinic.profile.domain.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CustomerProfileRequest(
        @Size(max = 20) String phone,
        @Past LocalDate dateOfBirth,
        Gender gender
) {
}
