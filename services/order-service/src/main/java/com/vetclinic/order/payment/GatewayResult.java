package com.vetclinic.order.payment;

import java.math.BigDecimal;

/** Kết quả cổng báo về, đã qua kiểm tra chữ ký. */
public record GatewayResult(String txnRef, BigDecimal amount, boolean success, String gatewayTransactionNo) {
}
