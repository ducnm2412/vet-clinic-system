package com.vetclinic.product.messaging;

import com.vetclinic.product.exception.InsufficientStockException;
import com.vetclinic.product.exception.ResourceNotFoundException;
import com.vetclinic.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** CN-31: nghe sự kiện đơn hàng thành công và trừ tồn kho tương ứng. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCompletedListener {

    private final ProductService productService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_COMPLETED_QUEUE)
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.info("Nhận sự kiện order.completed: orderId={}, {} dòng hàng",
                event.orderId(), event.lines() == null ? 0 : event.lines().size());

        if (event.lines() == null || event.lines().isEmpty()) {
            return;
        }

        for (OrderCompletedEvent.Line line : event.lines()) {
            try {
                boolean applied = productService.applySale(line.productId(), line.quantity(), event.orderId());
                if (applied) {
                    log.info("Đã trừ {} đơn vị của sản phẩm {} theo đơn {}",
                            line.quantity(), line.productId(), event.orderId());
                }
            } catch (ResourceNotFoundException | InsufficientStockException e) {
                // Ném lại sẽ khiến RabbitMQ giao lại vô hạn mà kết quả vẫn thế — sản phẩm đã bị
                // xoá hoặc kho thực sự không đủ. Ghi log để xử lý tay, và vẫn trừ nốt các dòng
                // còn lại của đơn thay vì bỏ dở giữa chừng.
                log.error("Không trừ được kho cho sản phẩm {} (đơn {}): {}",
                        line.productId(), event.orderId(), e.getMessage());
            }
        }
    }
}
