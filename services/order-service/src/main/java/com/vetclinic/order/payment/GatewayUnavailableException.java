package com.vetclinic.order.payment;

/** Hệ thống chưa bật cổng thanh toán online nào, nên không đặt hay trả đơn online được. */
public class GatewayUnavailableException extends RuntimeException {

    public GatewayUnavailableException() {
        super("Thanh toán online hiện chưa khả dụng, vui lòng chọn thanh toán khi nhận hàng");
    }
}
