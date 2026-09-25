package com.vetclinic.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * CN-19: lễ tân mở tài khoản cho khách đang đứng ở quầy.
 *
 * Khác {@link RegisterRequest} ở hai chỗ: không có confirmPassword (khách không tự gõ, nhân viên
 * gõ hộ rồi đọc lại cho khách), và có số điện thoại — khách vãng lai thì số điện thoại mới là
 * cách gọi lại được, không phải email.
 */
public record CreateCustomerAccountRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Pattern(regexp = "0\\d{9}", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0")
        String phone
) {
}
