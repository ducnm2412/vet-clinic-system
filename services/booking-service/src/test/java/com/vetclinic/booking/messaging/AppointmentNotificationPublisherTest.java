package com.vetclinic.booking.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Chỉ kiểm tra thư báo đi đúng exchange và routing key. Dùng RabbitTemplate giả thay vì RabbitMQ
 * thật: nếu bắn thật, notification-service đang chạy cùng broker sẽ nhận và gửi email thật.
 */
class AppointmentNotificationPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AppointmentEventPublisher publisher = new AppointmentEventPublisher(rabbitTemplate);

    @Test
    void publishesToBookingEventsWithTheNotificationRoutingKey() {
        AppointmentNotificationEvent event = new AppointmentNotificationEvent(
                AppointmentNotificationEvent.Type.PROPOSAL, UUID.randomUUID(), "khach@example.com", "Milo", null,
                null, "Khoa", LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), LocalTime.of(10, 30),
                LocalDate.of(2026, 12, 1), LocalTime.of(9, 0));

        publisher.onAppointmentNotification(event);

        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.BOOKING_EVENTS_EXCHANGE, "appointment.notification",
                event);
    }
}
