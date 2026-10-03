package com.vetclinic.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record FacebookLoginRequest(
        @NotBlank String accessToken
) {
}
