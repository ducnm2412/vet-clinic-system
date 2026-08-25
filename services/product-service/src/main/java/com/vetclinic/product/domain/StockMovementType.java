package com.vetclinic.product.domain;

public enum StockMovementType {
    /** Nhập kho từ nhà cung cấp. */
    IMPORT,
    /** Điều chỉnh tay sau kiểm kê (có thể âm hoặc dương). */
    ADJUSTMENT,
    /** Trừ kho khi đơn hàng thành công — sinh từ sự kiện RabbitMQ. */
    SALE,
    /** Hoàn kho khi đơn bị huỷ/trả. */
    RETURN
}
