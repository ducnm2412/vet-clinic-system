package com.vetclinic.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String line1,
        String line2,
        String ward,
        String city,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
