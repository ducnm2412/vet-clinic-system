package com.vetclinic.product.dto;

import java.util.UUID;

/**
 * {@code applied} = số dòng hàng thực sự vừa trừ kho. Bằng 0 nghĩa là đơn này đã trừ từ trước
 * (lệnh gọi được thử lại) — vẫn là thành công, không phải lỗi.
 */
public record StockDeductionResponse(UUID orderId, int applied) {
}
