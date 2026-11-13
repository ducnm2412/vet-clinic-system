package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import com.vetclinic.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentNotificationEventListenerTest {

    private final EmailService emailService = mock(EmailService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AppointmentNotificationEventListener listener =
            new AppointmentNotificationEventListener(emailService, notificationService);

    private static AppointmentNotificationEvent event(String type, UUID userId, String email) {
        return new AppointmentNotificationEvent(type, UUID.randomUUID(), userId, email, "Milo", null, null, null,
                LocalDate.now(), LocalTime.NOON, LocalTime.NOON.plusMinutes(30), null, null);
    }

    @Test
    void sendsBothTheEmailAndTheInAppNotification() {
        UUID user = UUID.randomUUID();
        AppointmentNotificationEvent event = event("CONFIRMED", user, "khach@example.com");
        when(emailService.sendAppointmentNotification(event)).thenReturn(true);

        listener.onAppointmentNotification(event);

        verify(emailService).sendAppointmentNotification(event);
        verify(notificationService).create(eq(user), eq("CONFIRMED"), anyString(), anyString(),
                eq("/appointments/" + event.appointmentId()));
    }

    @Test
    void withoutEmail_stillSavesTheInAppNotification_insteadOfFailingAndRetrying() {
        UUID user = UUID.randomUUID();

        listener.onAppointmentNotification(event("CONFIRMED", user, null));
        listener.onAppointmentNotification(event("CONFIRMED", user, " "));

        verify(emailService, never()).sendAppointmentNotification(any());
        verify(notificationService, org.mockito.Mockito.times(2))
                .create(eq(user), eq("CONFIRMED"), anyString(), anyString(), anyString());
    }

    @Test
    void withoutUserId_stillSendsTheEmail() {
        AppointmentNotificationEvent event = event("CONFIRMED", null, "khach@example.com");
        when(emailService.sendAppointmentNotification(event)).thenReturn(true);

        listener.onAppointmentNotification(event);

        verify(emailService).sendAppointmentNotification(event);
        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void anUnknownTypeIsNotAnError_andSavesNothing() {
        AppointmentNotificationEvent event = event("SOMETHING_NEW", UUID.randomUUID(), "khach@example.com");
        when(emailService.sendAppointmentNotification(event)).thenReturn(false);

        // Không ném: ném sẽ khiến RabbitMQ thử lại ba lần cho một loại thư không bao giờ gửi được.
        listener.onAppointmentNotification(event);

        verify(notificationService, never()).create(any(), any(), any(), any(), any());
    }
}
