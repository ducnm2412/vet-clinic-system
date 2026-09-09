package com.vetclinic.payment.messaging;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// @Transactional (giống MedicalRecordServiceTest/PaymentServiceTest): gọi thẳng listener để test
// LOGIC xử lý message (tạo Payment đúng, idempotent) với DB thật, không đi qua vòng publish/consume
// RabbitMQ thật — việc đó (routing key, exchange, message thực sự tới được consumer) đã được verify
// end-to-end thủ công (xem báo cáo Phase 7) và đúng pattern AppointmentEventPublisherTest cho chiều
// publish ở PaymentEventPublisherTest. Tách riêng để test logic không phụ thuộc thời gian khởi động
// consumer container (có thể mất vài giây khi Spring context vừa lên, không liên quan gì tới
// đúng/sai của logic tạo Payment).
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class PrescriptionCreatedEventListenerTest {

    @Autowired
    private PrescriptionCreatedEventListener listener;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void onPrescriptionCreated_createsPendingAmountPaymentWithItems() {
        UUID medicalRecordId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        PrescriptionCreatedEvent event = new PrescriptionCreatedEvent(medicalRecordId, appointmentId, customerUserId,
                List.of(new PrescriptionCreatedEvent.Item("Amoxicillin", "250mg", "2x/day", 7)));

        listener.onPrescriptionCreated(event);

        Optional<Payment> found = paymentRepository.findByMedicalRecordId(medicalRecordId);
        assertThat(found).isPresent();
        assertThat(found.get().getAppointmentId()).isEqualTo(appointmentId);
        assertThat(found.get().getCustomerUserId()).isEqualTo(customerUserId);
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.PENDING_AMOUNT);
        assertThat(found.get().getAmount()).isNull();
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).getMedicationName()).isEqualTo("Amoxicillin");
        assertThat(found.get().getItems().get(0).getDosage()).isEqualTo("250mg");
        assertThat(found.get().getItems().get(0).getFrequency()).isEqualTo("2x/day");
        assertThat(found.get().getItems().get(0).getDurationDays()).isEqualTo(7);
    }

    @Test
    void onPrescriptionCreated_redelivered_doesNotCreateDuplicatePayment() {
        UUID medicalRecordId = UUID.randomUUID();
        PrescriptionCreatedEvent event = new PrescriptionCreatedEvent(medicalRecordId, UUID.randomUUID(),
                UUID.randomUUID(), List.of(new PrescriptionCreatedEvent.Item("DrugA", "1 tab", "1x/day", 5)));

        // RabbitMQ là at-least-once — giả lập redelivery bằng cách gọi listener 2 lần với event
        // giống hệt nhau (cùng medicalRecordId).
        listener.onPrescriptionCreated(event);
        listener.onPrescriptionCreated(event);

        assertThat(paymentRepository.findAll()).hasSize(1);
    }
}
