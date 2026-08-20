package com.vetclinic.auth.dto;

import com.vetclinic.auth.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        List<String> roles,
        Instant createdAt
) {
}
