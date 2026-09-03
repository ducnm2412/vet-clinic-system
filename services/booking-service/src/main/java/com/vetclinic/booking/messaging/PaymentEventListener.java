package com.vetclinic.booking.messaging;

import com.vetclinic.booking.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final MedicalRecordService medicalRecordService;

    // payment-service (khi được xây) publish message này lên payment.events sau khi khách hàng
    // thanh toán xong đơn thuốc. Chuyển PENDING -> PAID để staff có thể tiếp nhận.
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment.completed event for medicalRecordId={}", event.medicalRecordId());
        medicalRecordService.markPrescriptionPaid(event.medicalRecordId());
    }
}
