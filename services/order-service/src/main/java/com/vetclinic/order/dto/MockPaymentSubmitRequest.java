package com.vetclinic.order.dto;

import com.vetclinic.order.payment.MockPaymentGateway;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Trang cổng giả lập gửi lại đúng các tham số của link thanh toán (kèm chữ ký) và lựa chọn của khách.
 * Chỉ dùng với cổng giả lập.
 */
public record MockPaymentSubmitRequest(
        @NotBlank @Size(max = 64) String txnRef,
        @NotBlank @Size(max = 20) String orderCode,
        @NotBlank @Size(max = 20) String amount,
        @NotBlank @Size(max = 128) String signature,
        @NotNull MockPaymentGateway.Outcome outcome
) {
}
