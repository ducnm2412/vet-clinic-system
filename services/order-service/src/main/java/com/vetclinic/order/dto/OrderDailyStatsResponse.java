package com.vetclinic.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * CN-46: số liệu đơn hàng của một ngày (giờ Việt Nam), cho reporting-service.
 *
 * Ba con số tính theo ba mốc khác nhau: tạo theo created_at, giao xong theo completed_at, huỷ
 * theo cancelled_at. Nên một đơn tạo hôm qua, giao hôm nay được đếm ở hai ngày khác nhau.
 */
public record OrderDailyStatsResponse(
        LocalDate date,
        long ordersCreated,
        long ordersCompleted,
        long ordersCancelled,
        BigDecimal revenue
) {
}
