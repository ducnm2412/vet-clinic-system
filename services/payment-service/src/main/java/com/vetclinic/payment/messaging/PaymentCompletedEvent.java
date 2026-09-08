package com.vetclinic.payment.messaging;

import java.util.UUID;

// Publish sau khi confirmCash() commit thành công. booking-service lắng nghe (PaymentEventListener,
// queue booking.payment-completed) để chuyển MedicalRecord PENDING -> PAID.
public record PaymentCompletedEvent(UUID paymentId, UUID medicalRecordId, UUID appointmentId) {
}
