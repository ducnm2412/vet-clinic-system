package com.vetclinic.booking.messaging;

import java.util.UUID;

// Payment-service publish event này SAU KHI khách hàng đã thanh toán xong đơn thuốc.
// Booking-service lắng nghe (xem PaymentEventListener) để chuyển PENDING -> PAID, cho phép
// staff tiếp nhận (receivePrescription) đơn thuốc này.
public record PaymentCompletedEvent(UUID paymentId, UUID medicalRecordId, UUID appointmentId) {
}
