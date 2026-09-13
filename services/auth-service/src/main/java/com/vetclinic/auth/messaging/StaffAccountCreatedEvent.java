package com.vetclinic.auth.messaging;

import java.util.UUID;

/**
 * Phát khi ADMIN tạo tài khoản DOCTOR/STAFF (VD-20).
 *
 * Họ tên chỉ nằm ở auth-service, nhưng trang công khai lấy danh sách bác sĩ từ profile-service.
 * Sự kiện này mang tên sang để profile-service lưu một bản — tránh để mỗi lần xem danh sách
 * bác sĩ lại phải gọi ngược sang auth-service.
 *
 * `fullName` theo thứ tự Việt Nam: biểu mẫu đặt "Họ" vào firstName và "Tên" vào lastName.
 */
public record StaffAccountCreatedEvent(UUID userId, String email, String fullName, String role) {
}
