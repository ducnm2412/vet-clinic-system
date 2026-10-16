package com.vetclinic.order.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Do payment-service phát (payment.amount-set) khi nhân viên lưu số tiền của một khoản khám.
 * Khai lại bản của phía nhận, khớp theo tên trường JSON, để hai service không dùng chung code.
 */
public record PaymentAmountSetEvent(UUID paymentId, UUID customerUserId, BigDecimal amount) {
}
