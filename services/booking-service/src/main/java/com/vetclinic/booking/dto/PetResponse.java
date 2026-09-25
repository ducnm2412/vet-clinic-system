package com.vetclinic.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Mirror của pet-service PetResponse — chỉ để deserialize kết quả gọi qua PetServiceClient.
// gender để String thay vì enum riêng vì booking-service không cần validate giá trị này; ownerUserId
// pet-service có trả nhưng ở đây không cần, Jackson bỏ qua trường lạ.
public record PetResponse(
        UUID id,
        String name,
        String species,
        String breed,
        String gender,
        LocalDate dateOfBirth,
        BigDecimal weightKg,
        // VD-22: dị ứng phải theo được tới màn hình khám của bác sĩ, không chỉ nằm trong hồ sơ.
        String allergies,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
