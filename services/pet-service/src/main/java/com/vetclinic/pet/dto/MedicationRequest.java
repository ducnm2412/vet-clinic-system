package com.vetclinic.pet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code active} để trống thì giữ nguyên (khi sửa) hoặc mặc định đang dùng (khi tạo): form sửa
 * thông thường không phải nhớ gửi lại cờ này. {@code unitPrice} để trống nghĩa là chưa có giá.
 */
public record MedicationRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String unit,
        String caution,
        Boolean active,
        @DecimalMin(value = "0", message = "Đơn giá không được âm")
        @DecimalMax(value = "9999999999", message = "Đơn giá quá lớn")
        BigDecimal unitPrice
) {
    /** Không kèm giá: giữ chữ ký cũ cho chỗ chỉ cần tên, đơn vị, lưu ý. */
    public MedicationRequest(String name, String unit, String caution, Boolean active) {
        this(name, unit, caution, active, null);
    }
}
