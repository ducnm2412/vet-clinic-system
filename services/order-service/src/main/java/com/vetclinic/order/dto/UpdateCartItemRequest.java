package com.vetclinic.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(
        // Muốn bỏ hẳn sản phẩm thì dùng DELETE, không truyền 0 vào đây.
        @NotNull @Min(1) Integer quantity
) {
}
