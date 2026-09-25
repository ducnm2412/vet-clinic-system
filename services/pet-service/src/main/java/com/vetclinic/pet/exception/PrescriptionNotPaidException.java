package com.vetclinic.pet.exception;

/** Đơn thuốc chưa thanh toán thì quầy không giao thuốc được — trả 409. */
public class PrescriptionNotPaidException extends RuntimeException {

    public PrescriptionNotPaidException(String message) {
        super(message);
    }
}
