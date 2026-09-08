package com.vetclinic.payment.domain;

public enum PaymentMethod {
    // Thanh toán tiền mặt tại quầy — staff xác nhận thủ công.
    CASH,
    // Chuyển khoản ngân hàng — staff đối soát rồi xác nhận thủ công.
    BANK_TRANSFER,
    // Quẹt thẻ tại quầy.
    CARD
}
