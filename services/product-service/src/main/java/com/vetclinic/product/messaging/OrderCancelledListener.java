package com.vetclinic.product.messaging;

import com.vetclinic.product.exception.ResourceNotFoundException;
import com.vetclinic.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Hoàn hàng về kho khi đơn đã xác nhận bị huỷ. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledListener {

    private final ProductService productService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCELLED_QUEUE)
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Nhận sự kiện order.cancelled: orderId={}, {} dòng hàng",
                event.orderId(), event.lines() == null ? 0 : event.lines().size());

        if (event.lines() == null || event.lines().isEmpty()) {
            return;
        }

        for (OrderCancelledEvent.Line line : event.lines()) {
            try {
                boolean applied = productService.applyReturn(line.productId(), line.quantity(), event.orderId());
                if (applied) {
                    log.info("Đã hoàn {} đơn vị của sản phẩm {} theo đơn huỷ {}",
                            line.quantity(), line.productId(), event.orderId());
                }
            } catch (ResourceNotFoundException e) {
                // Sản phẩm đã bị xoá — ném lại chỉ khiến broker giao lại vô hạn.
                log.error("Không hoàn được kho cho sản phẩm {} (đơn {}): {}",
                        line.productId(), event.orderId(), e.getMessage());
            }
        }
    }
}
