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
import static org.mockito.Mockito.when;

class AppointmentNotificationEventListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final AppointmentNotificationEventListener listener =
            new AppointmentNotificationEventListener(emailService);

    private static AppointmentNotificationEvent withEmail(String email) {
        return new AppointmentNotificationEvent("CONFIRMED", UUID.randomUUID(), email, "Milo", null, null, null,
                LocalDate.now(), LocalTime.NOON, LocalTime.NOON.plusMinutes(30), null, null);
    }

    @Test
    void sendsWhenEmailPresent() {
        AppointmentNotificationEvent event = withEmail("khach@example.com");
        when(emailService.sendAppointmentNotification(event)).thenReturn(true);

        listener.onAppointmentNotification(event);

        verify(emailService).sendAppointmentNotification(event);
    }

    @Test
    void skipsWithoutEmail_insteadOfFailingAndRetrying() {
        listener.onAppointmentNotification(withEmail(null));
        listener.onAppointmentNotification(withEmail(" "));
        verify(emailService, never()).sendAppointmentNotification(any());
    }

    @Test
    void anUnknownTypeIsNotAnError() {
        AppointmentNotificationEvent event = withEmail("khach@example.com");
        when(emailService.sendAppointmentNotification(event)).thenReturn(false);

        // Không ném: ném sẽ khiến RabbitMQ thử lại ba lần cho một loại thư không bao giờ gửi được.
        listener.onAppointmentNotification(event);
    }
}
