package com.vetclinic.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Thay toàn bộ dòng sản phẩm trên hoá đơn quầy đang chờ thu. Giá và kho lấy từ product-service. */
public record ReplaceCounterItemsRequest(
        @Valid @NotNull @Size(max = 50) List<CounterInvoiceRequest.Line> items
) {
}
