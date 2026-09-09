package com.vetclinic.booking.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    private static final String ROUTING_KEY_APPOINTMENT_CREATED = "appointment.created";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: chỉ publish khi transaction tạo appointment đã commit thành công, tránh
    // báo "đã đặt lịch" cho message queue trong khi DB rollback (giống UserEventPublisher bên
    // auth-service).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.BOOKING_EVENTS_EXCHANGE, ROUTING_KEY_APPOINTMENT_CREATED, event);
        log.info("Published appointment.created event for appointmentId={}", event.appointmentId());
    }
}
