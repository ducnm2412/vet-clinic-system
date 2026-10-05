package com.vetclinic.auth.messaging;

import java.util.UUID;

/**
 * CN-19: phát khi nhân viên mở tài khoản cho khách tại quầy.
 *
 * Mang sẵn số điện thoại sang profile-service để hồ sơ khách có luôn cách liên lạc — khách vãng
 * lai thường không bao giờ tự mở trang hồ sơ để điền.
 *
 * Khách tự đăng ký online thì vẫn là {@code user.registered} như cũ: luồng đó cần gửi email xác
 * minh, còn ở quầy thì người thật đang đứng đó nên tài khoản mở ra là dùng được ngay.
 */
public record CustomerAccountCreatedEvent(UUID userId, String email, String fullName, String phone) {
}
