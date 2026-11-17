package com.vetclinic.order.scheduler;

import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quét định kỳ và tự hoàn tất các đơn đã giao quá lâu mà khách không bấm "Đã nhận hàng", để đơn không treo ở
 * "Đang giao" mãi. Đặt {@code order.auto-complete-after-days} bằng 0 để tắt.
 */
@Slf4j
@Component
public class ShippedOrderAutoCompleteJob {

    // Mỗi lượt xử lý tối đa chừng này đơn; phần còn lại để lượt sau.
    static final int BATCH_SIZE = 200;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final int afterDays;

    public ShippedOrderAutoCompleteJob(OrderRepository orderRepository, OrderService orderService,
                                       @Value("${order.auto-complete-after-days:7}") int afterDays) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.afterDays = afterDays;
    }

    @Scheduled(fixedDelayString = "${order.auto-complete-scan-interval-ms:3600000}",
            initialDelayString = "${order.auto-complete-scan-interval-ms:3600000}")
    public void run() {
        if (afterDays <= 0) {
            return;
        }
        List<UUID> ids = orderRepository.findShippingOrderIdsShippedBefore(
                Instant.now().minus(Duration.ofDays(afterDays)), PageRequest.ofSize(BATCH_SIZE));
        int completed = 0;
        for (UUID id : ids) {
            try {
                if (orderService.autoCompleteShippedOrder(id, afterDays)) {
                    completed++;
                }
            } catch (RuntimeException e) {
                // Một đơn lỗi không được chặn các đơn còn lại; lượt sau sẽ thử lại.
                log.error("Không tự hoàn tất được đơn {}", id, e);
            }
        }
        if (completed > 0) {
            log.info("Đã tự hoàn tất {} đơn đã giao quá {} ngày", completed, afterDays);
        }
    }
}
