package com.vetclinic.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// Mirror của pet-service PetResponse — chỉ để deserialize kết quả gọi qua PetServiceClient.
// gender để String thay vì enum riêng vì booking-service không cần validate giá trị này.
// ownerUserId cần cho CN-19: lễ tân đặt lịch hộ thì phải đối chiếu con vật có đúng của khách đó
// không — nhân viên không phải chủ nên không hỏi được /pets/me.
public record PetResponse(
        UUID id,
        UUID ownerUserId,
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
