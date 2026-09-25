package com.vetclinic.pet.exception;

/** Không hỏi được booking-service để xác nhận lịch hẹn — trả 503, không phải 500. */
public class BookingUnavailableException extends RuntimeException {

    public BookingUnavailableException(String message) {
        super(message);
    }
}
