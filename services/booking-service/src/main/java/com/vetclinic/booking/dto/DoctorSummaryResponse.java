package com.vetclinic.booking.dto;

import java.util.UUID;

// Mirror của profile-service DoctorPublicResponse — chỉ để deserialize kết quả GET /profile/doctors
// (public, không cần token) qua ProfileServiceClient, dùng cho auto-generate slots.
public record DoctorSummaryResponse(
        UUID id,
        UUID userId,
        String specialty,
        String bio,
        Integer yearsOfExperience
) {
}
