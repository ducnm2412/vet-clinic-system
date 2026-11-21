package com.vetclinic.booking.messaging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Thư báo khách về lịch đặt theo yêu cầu. Một loại sự kiện với trường {@code type} thay vì bốn
 * loại riêng: notification-service chỉ cần một hàng đợi và một listener, thêm một loại thư mới
 * không phải khai thêm routing key ở hai nơi.
 *
 * Mang sẵn mọi thứ để soạn thư (email, tên bé, tên bác sĩ) nên notification-service không phải
 * gọi ngược sang service khác — như {@link AppointmentCreatedEvent}.
 */
public record AppointmentNotificationEvent(
        Type type,
        UUID appointmentId,
        // Chủ lịch, để notification-service lưu thông báo trong app cho đúng người. Không phụ thuộc
        // customerEmail: lịch không có email vẫn có người nhận trong app.
        UUID customerUserId,
        // Có thể null (lịch cũ, tạo hộ tại quầy): khi đó chỉ bỏ qua thư email.
        String customerEmail,
        String petName,
        String serviceName,
        String reason,
        // Tên bác sĩ phụ trách; null khi chưa xếp (REQUEST_RECEIVED) hoặc không tra được.
        String doctorName,
        // Giờ hiện hành của lịch: giờ khách mong muốn (REQUEST_RECEIVED, CANCELLED_BY_CLINIC khi
        // chưa xếp), giờ đã chốt (CONFIRMED) hoặc giờ đề xuất (PROPOSAL).
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        // Giờ khách chọn ban đầu, để thư đề xuất so sánh được "bạn chọn X, chúng tôi đề xuất Y".
        LocalDate preferredDate,
        LocalTime preferredTime,
        // Bác sĩ đã nhận lịch này (CONFIRMED, hoặc lịch đã xếp bị huỷ), để notification-service báo
        // cho bác sĩ. Null khi chưa có bác sĩ nhận — kể cả lúc đang đề xuất giờ khác.
        UUID doctorUserId
) {

    public enum Type {
        /** Phòng khám đã nhận yêu cầu, chưa có bác sĩ. */
        REQUEST_RECEIVED,
        /** Đã xếp bác sĩ (admin xếp, hoặc khách đồng ý giờ đề xuất). */
        CONFIRMED,
        /** Không xếp được đúng giờ khách chọn; đề xuất giờ khác chờ khách trả lời. */
        PROPOSAL,
        /** Phòng khám huỷ lịch (khách không tự huỷ). */
        CANCELLED_BY_CLINIC,
        /** Khách tự huỷ lịch đã xếp bác sĩ. Chỉ để báo bác sĩ — khách không nhận thư. */
        CANCELLED_BY_CUSTOMER,
        /** Phòng khám huỷ yêu cầu chờ xếp bác sĩ vì đã quá giờ khách chọn. */
        CANCELLED_OVERDUE
    }
}
