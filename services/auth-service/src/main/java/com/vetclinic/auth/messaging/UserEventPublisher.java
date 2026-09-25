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
    private static final String ROUTING_KEY_STAFF_CREATED = "user.staff-created";
    private static final String ROUTING_KEY_CUSTOMER_CREATED = "user.customer-created";
    private static final String ROUTING_KEY_USER_LOCKED = "user.locked";
    private static final String ROUTING_KEY_USER_UNLOCKED = "user.unlocked";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: chỉ đẩy message lên RabbitMQ khi transaction xoá user đã commit thành công.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerAccountCreated(CustomerAccountCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_CUSTOMER_CREATED, event);
        log.info("Published user.customer-created event for userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserDeletedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_USER_DELETED, event);
        log.info("Published user.deleted event for userId={}", event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStaffAccountCreated(StaffAccountCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_STAFF_CREATED, event);
        log.info("Published user.staff-created event for userId={} role={}", event.userId(), event.role());
    }

    // CN-08. AFTER_COMMIT: khoá bị rollback thì booking-service không được chặn lịch của bác sĩ.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        String routingKey = event.locked() ? ROUTING_KEY_USER_LOCKED : ROUTING_KEY_USER_UNLOCKED;
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, routingKey, event);
        log.info("Published {} event for userId={}", routingKey, event.userId());
    }

    // AFTER_COMMIT: chỉ đẩy message lên RabbitMQ khi transaction đăng ký user đã commit thành
    // công — tránh trường hợp DB rollback nhưng notification-service vẫn lỡ gửi email chào mừng.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_USER_REGISTERED, event);
        log.info("Published user.registered event for userId={}", event.userId());
    }
}
