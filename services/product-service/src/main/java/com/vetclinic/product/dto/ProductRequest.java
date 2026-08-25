package com.vetclinic.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 30) String unit,
        @Size(max = 500) String imageUrl,
        // Tồn kho ban đầu khi tạo mới; khi cập nhật sản phẩm thì bỏ qua trường này,
        // muốn đổi tồn phải đi qua endpoint nhập/điều chỉnh kho để có vết trong lịch sử.
        @PositiveOrZero Integer initialStock,
        @PositiveOrZero Integer lowStockThreshold,
        Boolean active
) {
}
