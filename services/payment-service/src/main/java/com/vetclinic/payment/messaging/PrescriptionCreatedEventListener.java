package com.vetclinic.payment.messaging;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentItem;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionCreatedEventListener {

    private final PaymentRepository paymentRepository;

    @RabbitListener(queues = RabbitMQConfig.PRESCRIPTION_CREATED_QUEUE)
    public void onPrescriptionCreated(PrescriptionCreatedEvent event) {
        // Idempotent: Rabbit chỉ đảm bảo at-least-once, message có thể bị redeliver -> tránh tạo
        // trùng Payment cho cùng 1 medicalRecordId (UNIQUE constraint ở DB là lưới an toàn cuối).
        if (paymentRepository.findByMedicalRecordId(event.medicalRecordId()).isPresent()) {
            log.info("Payment already exists for medicalRecordId={}, skip", event.medicalRecordId());
            return;
        }

        Payment payment = Payment.builder()
                .medicalRecordId(event.medicalRecordId())
                .appointmentId(event.appointmentId())
                .customerUserId(event.customerUserId())
                .status(PaymentStatus.PENDING_AMOUNT)
                .build();

        List<PaymentItem> items = event.items().stream()
                .map(item -> PaymentItem.builder()
                        .payment(payment)
                        .medicationName(item.medicationName())
                        .dosage(item.dosage())
                        .frequency(item.frequency())
                        .durationDays(item.durationDays())
                        .build())
                .toList();
        payment.setItems(items);

        paymentRepository.save(payment);
        log.info("Created Payment (PENDING_AMOUNT) for medicalRecordId={}, {} item(s)",
                event.medicalRecordId(), items.size());
    }
}
