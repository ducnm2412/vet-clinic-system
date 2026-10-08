package com.vetclinic.order.domain;

public enum PaymentMethod {
    /** Thanh toán khi nhận hàng. Khách đặt qua giỏ hàng chọn được. */
    COD,

    /** Trả qua cổng thanh toán lúc đặt hàng. Khách đặt qua giỏ hàng chọn được; đơn phải trả trước hạn. */
    ONLINE,

    /** Thu tiền mặt tại quầy. Chỉ nhân viên chọn được. */
    CASH,

    /** Chuyển khoản, nhân viên tự đối chiếu rồi ghi mã giao dịch. Chỉ nhân viên chọn được. */
    BANK_TRANSFER
}
