package com.vetclinic.order.scheduler;

import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.service.ExpiredOnlineOrderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Quét định kỳ và tự huỷ các đơn thanh toán online quá hạn mà khách chưa trả. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOnlineOrderExpiryJob {

    // Mỗi lượt xử lý tối đa chừng này đơn; phần còn lại để lượt sau, tránh một lượt chạy quá lâu.
    static final int BATCH_SIZE = 200;

    // Chờ thêm chừng này sau hạn rồi mới xét: giao dịch khách bấm trả sát giờ cần thời gian để cổng chốt.
    static final Duration GRACE = Duration.ofMinutes(2);

    private final OrderRepository orderRepository;
    private final ExpiredOnlineOrderProcessor processor;

    @Scheduled(fixedDelayString = "${order.expiry-scan-interval-ms:60000}",
            initialDelayString = "${order.expiry-scan-interval-ms:60000}")
    public void run() {
        List<UUID> ids = orderRepository.findExpiredUnpaidOnlineOrderIds(Instant.now().minus(GRACE), PageRequest.ofSize(BATCH_SIZE));
        int cancelled = 0;
        for (UUID id : ids) {
            try {
                if (processor.process(id)) {
                    cancelled++;
                }
            } catch (RuntimeException e) {
                // Một đơn lỗi không được chặn các đơn còn lại; lượt sau sẽ thử lại.
                log.error("Không huỷ được đơn quá hạn {}", id, e);
            }
        }
        if (cancelled > 0) {
            log.info("Đã tự huỷ {} đơn online quá hạn thanh toán", cancelled);
        }
    }
}
