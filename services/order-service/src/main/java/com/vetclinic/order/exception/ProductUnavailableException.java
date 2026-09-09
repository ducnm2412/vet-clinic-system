package com.vetclinic.order.exception;

/** Sản phẩm đã bị xoá/ngừng bán, hoặc kho không đủ số lượng khách đặt. */
public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException(String message) {
        super(message);
    }
}
