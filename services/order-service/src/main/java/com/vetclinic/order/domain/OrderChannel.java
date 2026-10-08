package com.vetclinic.order.domain;

public enum OrderChannel {

    /** Khách đặt qua giỏ hàng, giao tận nơi, thanh toán khi nhận hàng. */
    ONLINE,

    /** Nhân viên lập và thu tiền ngay tại quầy, có thể gộp cả tiền khám. */
    COUNTER
}
