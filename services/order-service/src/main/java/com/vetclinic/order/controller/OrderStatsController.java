package com.vetclinic.order.controller;

import com.vetclinic.order.dto.OrderDailyStatsResponse;
import com.vetclinic.order.service.OrderStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * CN-46: nguồn số liệu đơn hàng cho reporting-service. Chỉ ADMIN — khai trong SecurityConfig.
 *
 * Đường dẫn literal "/orders/stats" được Spring ưu tiên hơn mẫu "/orders/{id}".
 */
@RestController
@RequiredArgsConstructor
public class OrderStatsController {

    private final OrderStatsService orderStatsService;

    @GetMapping("/orders/stats")
    public List<OrderDailyStatsResponse> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderStatsService.daily(from, to);
    }
}
