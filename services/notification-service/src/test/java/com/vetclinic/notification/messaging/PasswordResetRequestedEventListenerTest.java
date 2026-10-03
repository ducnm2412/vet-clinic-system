package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class PasswordResetRequestedEventListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final PasswordResetRequestedEventListener listener =
            new PasswordResetRequestedEventListener(emailService);

    @Test
    void onPasswordResetRequested_callsEmailServiceWithFieldsExtractedFromEvent() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(UUID.randomUUID(),
                "jane@example.com", "Jane", "reset-tok", expiresAt);

        listener.onPasswordResetRequested(event);

        verify(emailService).sendPasswordResetEmail("jane@example.com", "Jane", "reset-tok", expiresAt);
        verifyNoMoreInteractions(emailService);
    }
}
