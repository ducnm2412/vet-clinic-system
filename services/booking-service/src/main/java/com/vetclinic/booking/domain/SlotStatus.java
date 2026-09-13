package com.vetclinic.booking.domain;

public enum SlotStatus {
    AVAILABLE,
    BOOKED,
    CANCELLED,
    /** CN-08: bác sĩ bị khoá tài khoản. Mở khoá thì slot tương lai trở lại AVAILABLE. */
    BLOCKED
}
