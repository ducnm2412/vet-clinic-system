package com.vetclinic.notification.messaging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Mirror của booking-service AppointmentNotificationEvent. `type` giữ dạng chuỗi (REQUEST_RECEIVED,
 * CONFIRMED, PROPOSAL, CANCELLED_BY_CLINIC) thay vì enum: booking-service thêm loại mới trước khi
 * bản này kịp cập nhật thì chỉ bỏ qua thư đó, không làm hỏng việc đọc cả message.
 */
public record AppointmentNotificationEvent(
        String type,
        UUID appointmentId,
        String customerEmail,
        String petName,
        String serviceName,
        String reason,
        String doctorName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate preferredDate,
        LocalTime preferredTime
) {
}
