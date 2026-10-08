package com.vetclinic.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vetclinic.order.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Nhân viên lập và thu hoá đơn tại quầy trong một lần: tiền khám/thuốc (nếu có) cộng các sản phẩm
 * khách mua. Giá, số tiền khám và khách hàng đều lấy từ server, không nhận từ request.
 */
public record CounterInvoiceRequest(
        /* Khoản khám đang chờ thu ở payment-service; bỏ trống nếu khách chỉ mua đồ. */
        UUID examPaymentId,
        /* Chỉ để in trên hoá đơn. Khách có tài khoản thì gắn theo khoản khám, không theo hai trường này. */
        @Size(max = 200) String customerName,
        @Size(max = 20)
        @Pattern(regexp = "^(0[0-9]{9})?$", message = "số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
        String customerPhone,
        @Valid @Size(max = 50) List<Line> items,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 100) String transferReference,
        @Size(max = 500) String note
) {

    public record Line(
            @NotNull UUID productId,
            @Min(1) @Max(1000) int quantity
    ) {
    }

    @JsonIgnore
    @AssertTrue(message = "hoá đơn phải có tiền khám hoặc ít nhất một sản phẩm")
    public boolean isContentPresent() {
        return examPaymentId != null || (items != null && !items.isEmpty());
    }

    /** Tại quầy chỉ thu tiền mặt hoặc chuyển khoản; COD là cách thu khi giao hàng. */
    @JsonIgnore
    @AssertTrue(message = "tại quầy chỉ thu bằng tiền mặt (CASH) hoặc chuyển khoản (BANK_TRANSFER)")
    public boolean isCounterPaymentMethod() {
        return paymentMethod == null
                || paymentMethod == PaymentMethod.CASH || paymentMethod == PaymentMethod.BANK_TRANSFER;
    }
}
