package com.vetclinic.payment.messaging;

import java.util.List;
import java.util.UUID;

// Bản sao riêng của payment-service cho event prescription.created (booking-service publish) —
// mỗi service tự sở hữu model của mình, Jackson chỉ cần khớp tên field JSON, không cần dùng
// chung class giữa hai service.
public record PrescriptionCreatedEvent(
        UUID medicalRecordId,
        UUID appointmentId,
        UUID customerUserId,
        List<Item> items
) {
    public record Item(String medicationName, String dosage, String frequency, Integer durationDays) {
    }
}
