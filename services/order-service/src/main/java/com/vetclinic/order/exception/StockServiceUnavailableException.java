package com.vetclinic.order.exception;

/**
 * VD-14: không hỏi được product-service lúc xác nhận đơn (service tắt, quá hạn, lỗi lạ).
 *
 * Trả 503 chứ không 500: đây không phải lỗi dữ liệu, nhân viên bấm lại sau là được. Quan trọng
 * hơn, đơn KHÔNG được xác nhận khi chưa chắc còn hàng.
 */
public class StockServiceUnavailableException extends RuntimeException {

    public StockServiceUnavailableException(Throwable cause) {
        super("Chưa kiểm tra được tồn kho, thử xác nhận lại sau ít phút", cause);
    }
}
