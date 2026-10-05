package com.vetclinic.pet.messaging;

import java.util.UUID;

/**
 * payment-service phát sau khi khách đã trả tiền đơn thuốc. Trước đây booking-service nghe;
 * từ 25/09/2026 bệnh án ở pet-service nên chỗ nghe cũng chuyển sang đây (VD-10 chặng 2).
 */
public record PaymentCompletedEvent(UUID paymentId, UUID medicalRecordId, UUID appointmentId) {
}
