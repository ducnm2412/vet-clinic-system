package com.vetclinic.payment.domain;

public enum PaymentStatus {
    // Vừa tạo từ prescription.created (booking-service) — chưa có amount, chờ staff nhập tay.
    PENDING_AMOUNT,
    // Staff đã nhập amount (PUT .../amount) — chờ khách hàng thanh toán.
    PENDING_PAYMENT,
    // Đã thanh toán xong — payment-service publish payment.completed cho booking-service.
    COMPLETED,
    // Huỷ (vd: khách không lấy thuốc nữa) — không tính là đã thanh toán.
    CANCELLED
}
