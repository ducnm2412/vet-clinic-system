package com.vetclinic.pet.exception;

/** Đã có thuốc trùng tên (không phân biệt hoa/thường và dấu) trong danh mục — trả 409. */
public class DuplicateMedicationException extends RuntimeException {

    public DuplicateMedicationException(String message) {
        super(message);
    }
}
