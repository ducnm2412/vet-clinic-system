package com.vetclinic.pet.exception;

/** Nội dung đơn mẫu không dùng được (thuốc không có trong danh mục hoặc đã bị ẩn) — trả 400. */
public class InvalidTemplateException extends RuntimeException {

    public InvalidTemplateException(String message) {
        super(message);
    }
}
