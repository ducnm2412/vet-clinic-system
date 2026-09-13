package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AppointmentCreatedEventListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final AppointmentCreatedEventListener listener = new AppointmentCreatedEventListener(emailService);

    private static AppointmentCreatedEvent withEmail(String email) {
        return new AppointmentCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.now(), LocalTime.NOON, LocalTime.NOON.plusMinutes(30),
                email, "Milo", null, null);
    }

    @Test
    void sendsWhenEmailPresent() {
        AppointmentCreatedEvent event = withEmail("khach@example.com");
        listener.onAppointmentCreated(event);
        verify(emailService).sendAppointmentConfirmation(event);
    }

    @Test
    void skipsWithoutEmail_insteadOfFailingAndRetrying() {
        listener.onAppointmentCreated(withEmail(null));
        listener.onAppointmentCreated(withEmail(" "));
        verify(emailService, never()).sendAppointmentConfirmation(any());
    }
}
