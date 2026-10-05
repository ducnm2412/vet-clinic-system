package com.vetclinic.pet.dto;

import com.vetclinic.pet.domain.PetGender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Giữ nguyên hình dạng cũ của profile-service để frontend và booking-service không phải đổi
 * cách đọc. Thêm {@code ownerUserId} cho màn hình quản trị ghép chủ nuôi.
 */
public record PetResponse(
        UUID id,
        UUID ownerUserId,
        String name,
        String species,
        String breed,
        PetGender gender,
        LocalDate dateOfBirth,
        BigDecimal weightKg,
        /** Dị ứng — null khi chủ nuôi chưa khai. Khác hẳn "đã khai là không dị ứng gì". */
        String allergies,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
