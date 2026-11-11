package com.vetclinic.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code active} để trống thì giữ nguyên (khi sửa) hoặc mặc định đang dùng (khi tạo): form sửa
 * thông thường không phải nhớ gửi lại cờ này.
 */
public record MedicationRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String unit,
        String caution,
        Boolean active
) {
}
