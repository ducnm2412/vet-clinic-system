package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetRequestedEventListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.PASSWORD_RESET_REQUESTED_QUEUE)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        emailService.sendPasswordResetEmail(event.email(), event.firstName(), event.resetToken(),
                event.resetTokenExpiresAt());
        // Không log token: ai đọc được log là đặt lại được mật khẩu của người đó.
        log.info("Processed user.password-reset-requested event for userId={}", event.userId());
    }
}
