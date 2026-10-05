package com.vetclinic.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * VD-14: order-service xin trừ kho cho một đơn vừa được nhân viên xác nhận.
 *
 * {@code orderId} vừa là tham chiếu ghi vào vết kho, vừa là khoá chống trừ hai lần khi lệnh gọi
 * được thử lại.
 */
public record StockDeductionRequest(
        @NotNull UUID orderId,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(@NotNull UUID productId, @NotNull @Min(1) Integer quantity) {
    }
}
