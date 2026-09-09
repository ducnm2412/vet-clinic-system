package com.vetclinic.booking.domain;

public enum PrescriptionStatus {
    // Bác sĩ vừa kê xong, chờ khách hàng thanh toán.
    PENDING,
    // Khách hàng đã thanh toán — sẵn sàng để staff tiếp nhận và giao thuốc.
    PAID,
    // Staff đã tiếp nhận đơn thuốc.
    RECEIVED
}
