package com.vetclinic.order.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy sự kiện lên RabbitMQ CHỈ SAU KHI transaction commit thành công.
 *
 * Nếu publish thẳng trong service, transaction rollback sau đó sẽ để lại một message
 * "đơn đã xác nhận" đã bay đi — product-service trừ kho cho một đơn không tồn tại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_ORDER_COMPLETED, event);
        log.info("Đã phát order.completed cho đơn {} ({} dòng hàng)", event.orderId(), event.lines().size());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EVENTS_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_ORDER_CANCELLED, event);
        log.info("Đã phát order.cancelled cho đơn {} ({} dòng hàng)", event.orderId(), event.lines().size());
    }
}
