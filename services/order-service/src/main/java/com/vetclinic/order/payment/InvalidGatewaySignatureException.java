package com.vetclinic.order.payment;

/** Kết quả từ cổng thanh toán không qua được kiểm tra chữ ký: coi như giả mạo, không được tin. */
public class InvalidGatewaySignatureException extends RuntimeException {

    public InvalidGatewaySignatureException(String message) {
        super(message);
    }
}
