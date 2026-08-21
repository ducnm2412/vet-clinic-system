package com.vetclinic.profile.dto;

import java.util.UUID;

// Dùng cho GET /profile/doctors (public) — cố ý KHÔNG có phone, đây là dữ liệu liên hệ cá nhân
// không nên lộ ra ngoài trang đặt lịch công khai.
public record DoctorPublicResponse(
        UUID id,
        UUID userId,
        String specialty,
        String bio,
        Integer yearsOfExperience
) {
}
