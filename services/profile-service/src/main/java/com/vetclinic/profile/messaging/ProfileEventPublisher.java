package com.vetclinic.profile.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileEventPublisher {

    private static final String ROUTING_KEY_PROFILE_UPDATED = "profile.updated";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: hồ sơ lưu lỗi (rollback) thì không được gỡ thông báo nhắc.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileUpdated(ProfileUpdatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.USER_EVENTS_EXCHANGE, ROUTING_KEY_PROFILE_UPDATED, event);
        log.info("Published profile.updated event for userId={} role={}", event.userId(), event.role());
    }
}
