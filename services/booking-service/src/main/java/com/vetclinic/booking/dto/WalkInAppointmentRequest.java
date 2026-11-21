package com.vetclinic.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * CN-19: lễ tân đặt lịch hộ khách đang đứng ở quầy.
 *
 * Khác {@link AppointmentRequest} đúng một chỗ: khách là ai (và email của họ) nằm trong thân request
 * chứ không lấy từ token, vì người gọi là nhân viên. Phần còn lại dùng chung để hai đường đặt lịch
 * không trôi ra khỏi nhau theo thời gian.
 */
public record WalkInAppointmentRequest(
        @NotNull UUID customerUserId,
        // Email của khách (nhân viên chọn ở bước một): để báo khách khi admin xếp lịch. Không bắt buộc.
        @Email @Size(max = 255) String customerEmail,
        @NotNull @Valid AppointmentRequest appointment
) {
}
