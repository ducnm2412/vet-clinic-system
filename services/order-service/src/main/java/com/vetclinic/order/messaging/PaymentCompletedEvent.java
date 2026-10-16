package com.vetclinic.order.messaging;

import java.util.UUID;

/**
 * payment-service phát (payment.completed) khi khoản khám đã thu xong — cả thu qua hoá đơn quầy
 * lẫn gọi confirm-cash trực tiếp. Khai lại bản của phía nhận, khớp theo tên trường JSON.
 */
public record PaymentCompletedEvent(UUID paymentId, UUID medicalRecordId, UUID appointmentId) {
}
