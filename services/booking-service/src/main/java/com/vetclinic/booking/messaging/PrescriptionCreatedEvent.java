package com.vetclinic.booking.messaging;

import java.util.List;
import java.util.UUID;

// Bác sĩ vừa kê đơn thuốc lần đầu cho appointment này — payment-service (sẽ xây ở bước sau)
// lắng nghe event này để tạo yêu cầu thanh toán cho khách hàng.
// LƯU Ý: chưa có trường giá tiền vì hệ thống chưa có bảng giá thuốc — payment-service tự
// quyết định số tiền cần thu (vd: staff nhập tay) cho tới khi có price catalog.
public record PrescriptionCreatedEvent(
        UUID medicalRecordId,
        UUID appointmentId,
        UUID customerUserId,
        List<Item> items
) {
    public record Item(String medicationName, String dosage, String frequency, Integer durationDays) {
    }
}
