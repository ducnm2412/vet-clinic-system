package com.vetclinic.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Mirror của profile-service PetResponse — chỉ để deserialize kết quả gọi qua ProfileServiceClient,
// gender để String thay vì enum riêng vì booking-service không cần validate giá trị này.
public record PetResponse(
        UUID id,
        String name,
        String species,
        String breed,
        String gender,
        LocalDate dateOfBirth,
        BigDecimal weightKg,
        Instant createdAt,
        Instant updatedAt
) {
}
