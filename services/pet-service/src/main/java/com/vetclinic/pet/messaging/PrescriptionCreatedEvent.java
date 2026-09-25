package com.vetclinic.pet.messaging;

import com.vetclinic.pet.domain.MedicalRecord;

import java.util.List;
import java.util.UUID;

/**
 * Bác sĩ vừa kê đơn lần đầu cho lịch hẹn này — payment-service nghe để tạo phiếu thu.
 *
 * Giữ nguyên hình dạng của bản cũ do booking-service phát (cùng tên trường, cùng routing key
 * {@code prescription.created}), chỉ đổi exchange sang {@code pet.events}: payment-service chỉ phải
 * đổi chỗ nghe, không phải đổi cách đọc.
 *
 * Chưa có trường giá tiền vì hệ thống chưa có bảng giá thuốc — payment-service để phiếu thu ở
 * PENDING_AMOUNT cho nhân viên nhập tay.
 */
public record PrescriptionCreatedEvent(
        UUID medicalRecordId,
        UUID appointmentId,
        UUID customerUserId,
        List<Item> items
) {
    public record Item(String medicationName, String dosage, String frequency, Integer durationDays) {
    }

    public static PrescriptionCreatedEvent of(MedicalRecord record) {
        return new PrescriptionCreatedEvent(record.getId(), record.getAppointmentId(), record.getCustomerUserId(),
                record.getPrescriptionItems().stream()
                        .map(item -> new Item(item.getMedicationName(), item.getDosage(), item.getFrequency(),
                                item.getDurationDays()))
                        .toList());
    }
}
