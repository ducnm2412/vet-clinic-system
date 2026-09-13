package com.vetclinic.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** CN-46: tiền dịch vụ khám đã thu trong một ngày (giờ Việt Nam), cho reporting-service. */
public record PaymentDailyStatsResponse(LocalDate date, long payments, BigDecimal revenue) {
}
