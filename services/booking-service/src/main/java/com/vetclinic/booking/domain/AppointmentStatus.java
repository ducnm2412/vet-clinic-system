package com.vetclinic.booking.domain;

public enum AppointmentStatus {
    PENDING,
    /** Khách đã gửi yêu cầu theo giờ mong muốn; chờ admin xếp bác sĩ. Chưa có slot. */
    AWAITING_ASSIGNMENT,
    /** Admin đề xuất giờ khác; chờ khách đồng ý hoặc huỷ. Chưa có slot. */
    AWAITING_CUSTOMER,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
}
