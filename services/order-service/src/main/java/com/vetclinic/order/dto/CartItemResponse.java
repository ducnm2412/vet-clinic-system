package com.vetclinic.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String sku,
        String name,
        String unit,
        BigDecimal price,
        Integer quantity,
        BigDecimal lineTotal,
        /** Còn bán và còn đủ hàng không — để giao diện cảnh báo trước khi khách bấm đặt. */
        Boolean available,
        String unavailableReason
) {
}
