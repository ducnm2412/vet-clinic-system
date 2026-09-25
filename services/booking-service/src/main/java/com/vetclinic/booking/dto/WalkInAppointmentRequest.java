package com.vetclinic.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * CN-19: lễ tân đặt lịch hộ khách đang đứng ở quầy.
 *
 * Khác {@link AppointmentRequest} đúng một chỗ: khách là ai nằm trong thân request chứ không lấy
 * từ token, vì người gọi là nhân viên. Phần còn lại dùng chung để hai đường đặt lịch không trôi
 * ra khỏi nhau theo thời gian.
 */
public record WalkInAppointmentRequest(
        @NotNull UUID customerUserId,
        @NotNull @Valid AppointmentRequest appointment
) {
}
