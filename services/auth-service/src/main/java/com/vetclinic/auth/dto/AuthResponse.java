package com.vetclinic.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}
