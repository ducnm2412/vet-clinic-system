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
public class PrescriptionEventPublisher {

    private static final String ROUTING_KEY_PRESCRIPTION_CREATED = "prescription.created";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: chỉ publish khi transaction lưu đơn thuốc đã commit thành công (giống
    // AppointmentEventPublisher/UserEventPublisher) — tránh yêu cầu thanh toán cho một đơn
    // thuốc mà DB thực ra đã rollback.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrescriptionCreated(PrescriptionCreatedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.BOOKING_EVENTS_EXCHANGE, ROUTING_KEY_PRESCRIPTION_CREATED, event);
        log.info("Published prescription.created event for medicalRecordId={}", event.medicalRecordId());
    }
}
