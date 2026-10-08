package com.vetclinic.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vetclinic.order.domain.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^0[0-9]{9}$", message = "số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
        String recipientPhone,
        @NotBlank @Size(max = 500) String shippingAddress,
        @Size(max = 500) String note,
        @NotNull PaymentMethod paymentMethod
) {

    /**
     * Đặt qua giỏ hàng chỉ có COD hoặc ONLINE. CASH và BANK_TRANSFER là cách thu tại quầy, do nhân viên
     * chọn: nếu khách chọn được thì đơn tự nhận "đã trả tiền" mà không ai thu.
     */
    @JsonIgnore
    @AssertTrue(message = "chỉ hỗ trợ thanh toán khi nhận hàng (COD) hoặc thanh toán online (ONLINE)")
    public boolean isPaymentMethodAllowedForCustomers() {
        return paymentMethod == null
                || paymentMethod == PaymentMethod.COD || paymentMethod == PaymentMethod.ONLINE;
    }
}
