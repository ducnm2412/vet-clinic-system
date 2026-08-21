package com.vetclinic.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StaffProfileRequest(
        @NotBlank @Size(max = 100) String position,
        @Size(max = 20) String phone,
        @PastOrPresent LocalDate hireDate
) {
}
