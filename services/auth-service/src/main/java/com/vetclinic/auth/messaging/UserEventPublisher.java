package com.vetclinic.auth.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private static final String ROUTING_KEY_USER_DELETED = "user.deleted";
    private static final String ROUTING_KEY_USER_REGISTERED = "user.registered";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: chỉ đẩy message lên RabbitMQ khi transaction xoá user đã commit thành công.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserDeletedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_USER_DELETED, event);
        log.info("Published user.deleted event for userId={}", event.userId());
    }

    // AFTER_COMMIT: chỉ đẩy message lên RabbitMQ khi transaction đăng ký user đã commit thành
    // công — tránh trường hợp DB rollback nhưng notification-service vẫn lỡ gửi email chào mừng.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_USER_REGISTERED, event);
        log.info("Published user.registered event for userId={}", event.userId());
    }
}
