package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.AppointmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        // slotId và doctorUserId để trống khi lịch còn chờ xếp bác sĩ (AWAITING_*): date/startTime
        // lúc đó là giờ khách mong muốn.
        UUID slotId,
        // VD-16: bác sĩ phụ trách. Lấy từ slot — khách không chọn bác sĩ, hệ thống tự xếp, nên
        // response là chỗ duy nhất người ta biết được mình được xếp cho ai.
        UUID doctorUserId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        UUID customerUserId,
        UUID petId,
        // Tên dịch vụ trả kèm để màn hình không phải gọi thêm lượt nữa; null khi khách không chọn.
        UUID serviceId,
        String serviceName,
        String reason,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt,
        // Giờ phòng khám đề xuất khi không xếp được đúng giờ khách chọn (AWAITING_CUSTOMER).
        LocalDate proposedDate,
        LocalTime proposedStartTime,
        // Giờ khách đã chọn lúc gửi yêu cầu — khi phòng khám đề xuất giờ khác, date/startTime là giờ
        // đề xuất còn đây là giờ ban đầu, để so sánh.
        LocalDate preferredDate,
        LocalTime preferredStartTime
) {
}
