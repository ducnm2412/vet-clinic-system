package com.vetclinic.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LowStockThresholdRequest(
        @NotNull @Min(value = 0, message = "Ngưỡng không được âm") @Max(1_000_000) Integer threshold
) {
}
