package com.vetclinic.order.exception;

public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("Giỏ hàng đang trống, không thể đặt hàng");
    }
}
