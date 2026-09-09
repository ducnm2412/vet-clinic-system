package com.vetclinic.booking.exception;

public class PrescriptionNotPaidException extends RuntimeException {

    public PrescriptionNotPaidException(String message) {
        super(message);
    }
}
