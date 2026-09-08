package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.USER_REGISTERED_QUEUE)
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.sendVerificationEmail(event.email(), event.firstName(), event.verificationToken(),
                event.verificationTokenExpiresAt());
        log.info("Processed user.registered event for userId={}", event.userId());
    }
}
