package com.vetclinic.pet.messaging;

import com.vetclinic.pet.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final MedicalRecordService medicalRecordService;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Nhận payment.completed cho medicalRecordId={}", event.medicalRecordId());
        medicalRecordService.markPaid(event.medicalRecordId());
    }
}
