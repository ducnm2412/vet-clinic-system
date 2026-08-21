package com.vetclinic.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        @Size(max = 100) String ward,
        @NotBlank @Size(max = 100) String city,
        boolean isDefault
) {
}
