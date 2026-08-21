package com.vetclinic.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DoctorLicenseRequest(
        @NotBlank @Size(max = 100) String licenseNumber,
        @NotBlank @Size(max = 255) String issuedBy,
        @NotNull @PastOrPresent LocalDate issuedDate,
        LocalDate expiryDate
) {
}
