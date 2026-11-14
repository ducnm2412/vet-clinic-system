package com.vetclinic.payment.exception;

/** Yêu cầu sai nội dung (dòng thuốc không thuộc phiếu, thiếu dòng, tổng bằng 0...) — khác sai trạng thái. */
public class InvalidPaymentRequestException extends RuntimeException {

    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
