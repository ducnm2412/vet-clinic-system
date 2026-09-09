package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

// Pure unit test: chỉ kiểm tra listener trích đúng field từ event và gọi đúng EmailService,
// không đi qua RabbitMQ thật (việc exchange/queue/routing key đúng đã verify end-to-end thủ công
// ở các phase trước).
class UserRegisteredEventListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final UserRegisteredEventListener listener = new UserRegisteredEventListener(emailService);

    @Test
    void onUserRegistered_callsEmailServiceWithFieldsExtractedFromEvent() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        UserRegisteredEvent event = new UserRegisteredEvent(userId, "jane@example.com", "Jane", "Doe",
                "tok-123", expiresAt);

        listener.onUserRegistered(event);

        verify(emailService).sendVerificationEmail("jane@example.com", "Jane", "tok-123", expiresAt);
        verifyNoMoreInteractions(emailService);
    }
}
