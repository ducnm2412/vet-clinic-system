package com.vetclinic.profile.dto;

import java.util.List;
import java.util.UUID;

/**
 * Tóm tắt hồ sơ một khách cho bảng Khách hàng của admin. Họ tên và email không có ở đây — chúng
 * nằm ở auth-service, frontend ghép theo userId.
 *
 * {@code address}: địa chỉ mặc định, không có thì địa chỉ đầu tiên; null khi chưa khai.
 */
public record CustomerSummaryResponse(UUID userId, String phone, String address, List<String> petNames) {
}
