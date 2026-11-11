package com.vetclinic.pet.exception;

/** Đã có đơn mẫu trùng tên (không phân biệt hoa/thường và dấu) — trả 409. */
public class DuplicateTemplateException extends RuntimeException {

    public DuplicateTemplateException(String message) {
        super(message);
    }
}
