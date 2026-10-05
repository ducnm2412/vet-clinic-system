package com.vetclinic.payment.messaging;

import java.util.UUID;

// Publish sau khi confirmCash() commit thành công. pet-service lắng nghe (PaymentCompletedListener,
// queue pet.payment-completed) để đóng đơn thuốc đã trả tiền — trước 25/09/2026 là booking-service,
// bệnh án đã chuyển sang pet-service (VD-10 chặng 2).
public record PaymentCompletedEvent(UUID paymentId, UUID medicalRecordId, UUID appointmentId) {
}
