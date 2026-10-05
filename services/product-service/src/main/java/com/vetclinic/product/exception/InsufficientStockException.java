package com.vetclinic.product.exception;

import java.util.UUID;

/**
 * Kho không đủ hàng. Thông báo đi thẳng ra màn hình nhân viên (order-service trả nguyên văn khi
 * xác nhận đơn thất bại — VD-14), nên viết bằng tiếng Việt và nêu tên sản phẩm, không phải UUID.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName, String unit, int available, int requested) {
        super("Sản phẩm \"" + productName + "\" chỉ còn " + available + " " + unit + ", cần " + requested);
    }

    public InsufficientStockException(UUID productId, int available, int requested) {
        super("Sản phẩm " + productId + " chỉ còn " + available + ", cần " + requested);
    }
}
