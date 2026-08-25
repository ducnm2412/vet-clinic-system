package com.vetclinic.product.dto;

import com.vetclinic.product.domain.StockMovementType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @NotNull StockMovementType type,
        // Âm được khi ADJUSTMENT (kiểm kê thiếu), nhưng không bao giờ được bằng 0.
        @NotNull Integer quantityChange,
        @Size(max = 500) String note
) {
    @AssertTrue(message = "quantityChange không được bằng 0")
    public boolean isQuantityChangeNonZero() {
        return quantityChange == null || quantityChange != 0;
    }

    @AssertTrue(message = "chỉ ADJUSTMENT mới được nhận số âm; IMPORT và RETURN phải dương")
    public boolean isSignConsistentWithType() {
        if (type == null || quantityChange == null) {
            return true;
        }
        // SALE do sự kiện RabbitMQ sinh ra, không nhận qua API thủ công.
        return switch (type) {
            case IMPORT, RETURN -> quantityChange > 0;
            case ADJUSTMENT -> true;
            case SALE -> false;
        };
    }
}
