package com.vetclinic.staff.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftEventPublisher {

    private static final String ROUTING_KEY_SHIFT_ADDED = "shift.added";
    private static final String ROUTING_KEY_SHIFT_REMOVED = "shift.removed";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: ca chưa lưu được mà booking-service đã mở giờ khám thì khách đặt vào một ca
    // không tồn tại.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShiftChanged(ShiftChangedEvent event) {
        String routingKey = event.added() ? ROUTING_KEY_SHIFT_ADDED : ROUTING_KEY_SHIFT_REMOVED;
        rabbitTemplate.convertAndSend(RabbitMQConfig.STAFF_EVENTS_EXCHANGE, routingKey, event);
        log.info("Published {} cho user={} ngày={}", routingKey, event.userId(), event.date());
    }
}
