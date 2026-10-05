package com.vetclinic.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ClinicServiceRequest(
        // Chỉ chữ thường, số và dấu gạch ngang: slug đi vào đường dẫn và vào code chọn biểu tượng.
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9-]+", message = "Chỉ gồm chữ thường, số và dấu gạch ngang")
        String slug,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        // Một ca ngắn nhất 5 phút, dài nhất 8 tiếng — chặn số vô lý gõ nhầm.
        @Min(5) @Max(480) Integer durationMinutes,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal referencePrice,
        Integer displayOrder
) {
}
