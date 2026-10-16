package com.vetclinic.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vetclinic.order.domain.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Thu tiền hoá đơn quầy đang chờ. Tên và số điện thoại chỉ để in; giá và kho được kiểm lại từ server.
 */
public record CollectCounterInvoiceRequest(
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 200) String customerName,
        @Size(max = 20)
        @Pattern(regexp = "^(0[0-9]{9})?$", message = "số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
        String customerPhone,
        @Size(max = 100) String transferReference,
        @Size(max = 500) String note
) {

    @JsonIgnore
    @AssertTrue(message = "tại quầy chỉ thu bằng tiền mặt (CASH) hoặc chuyển khoản (BANK_TRANSFER)")
    public boolean isCounterPaymentMethod() {
        return paymentMethod == null
                || paymentMethod == PaymentMethod.CASH || paymentMethod == PaymentMethod.BANK_TRANSFER;
    }
}
