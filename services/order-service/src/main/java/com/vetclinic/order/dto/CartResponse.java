package com.vetclinic.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        Integer totalItems,
        BigDecimal subtotal,
        /** false khi có ít nhất một dòng không mua được — checkout sẽ bị từ chối. */
        Boolean checkoutable
) {
}
