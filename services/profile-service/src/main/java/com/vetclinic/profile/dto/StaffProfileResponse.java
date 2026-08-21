package com.vetclinic.profile.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffProfileResponse(
        UUID id,
        UUID userId,
        String position,
        String phone,
        LocalDate hireDate,
        Instant createdAt,
        Instant updatedAt
) {
}
