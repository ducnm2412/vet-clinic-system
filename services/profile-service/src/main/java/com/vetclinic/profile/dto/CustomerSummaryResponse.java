package com.vetclinic.profile.dto;

import java.util.UUID;

/**
 * Tóm tắt hồ sơ một khách cho bảng Khách hàng của admin. Họ tên và email không có ở đây — chúng
 * nằm ở auth-service, frontend ghép theo userId.
 *
 * {@code address}: địa chỉ mặc định, không có thì địa chỉ đầu tiên; null khi chưa khai.
 *
 * Tên thú cưng không còn ở đây — hồ sơ thú cưng đã chuyển sang pet-service, frontend lấy riêng
 * qua {@code GET /pets/by-owners}.
 */
public record CustomerSummaryResponse(UUID userId, String phone, String address) {
}
