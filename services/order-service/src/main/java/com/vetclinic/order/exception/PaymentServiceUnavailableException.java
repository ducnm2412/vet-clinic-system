package com.vetclinic.order.exception;

/**
 * Không đọc được khoản khám từ payment-service lúc lập hoá đơn tại quầy (service tắt, quá hạn,
 * lỗi lạ). Trả 503: nhân viên bấm lại sau là được, và hoá đơn không được lập khi chưa chắc số tiền.
 */
public class PaymentServiceUnavailableException extends RuntimeException {

    public PaymentServiceUnavailableException(Throwable cause) {
        super("Chưa lấy được khoản khám từ dịch vụ thanh toán, thử lại sau ít phút", cause);
    }
}
