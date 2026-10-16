package com.vetclinic.payment.messaging;

import java.math.BigDecimal;
import java.util.UUID;

// Publish sau khi staff lưu số tiền của một khoản khám (PENDING_AMOUNT -> PENDING_PAYMENT) và transaction
// đã commit. order-service nghe (routing key payment.amount-set) để dựng sẵn hoá đơn chờ thanh toán tại
// quầy cho khách. Số tiền chỉ nhập được một lần nên mỗi khoản khám chỉ phát sự kiện này tối đa một lần.
public record PaymentAmountSetEvent(UUID paymentId, UUID customerUserId, BigDecimal amount) {
}
