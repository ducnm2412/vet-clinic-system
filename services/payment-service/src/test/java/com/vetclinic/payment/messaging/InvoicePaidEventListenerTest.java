package com.vetclinic.payment.messaging;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentMethod;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Gọi thẳng listener để test LOGIC xử lý message với DB thật, không đi qua RabbitMQ thật (cùng cách
// PrescriptionCreatedEventListenerTest); routing key/queue được kiểm bằng E2E thật ở bước cuối.
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class InvoicePaidEventListenerTest {

    @Autowired
    private InvoicePaidEventListener listener;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void onInvoicePaid_completesTheExamPaymentWithTheCollectedMethod() {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .medicalRecordId(UUID.randomUUID())
                .appointmentId(UUID.randomUUID())
                .customerUserId(UUID.randomUUID())
                .status(PaymentStatus.PENDING_PAYMENT)
                .amount(new BigDecimal("200000"))
                .build());

        listener.onInvoicePaid(new InvoicePaidEvent(UUID.randomUUID(), payment.getId(), "BANK_TRANSFER"));

        Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reloaded.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void onInvoicePaid_forUnknownPayment_returnsNormally() {
        listener.onInvoicePaid(new InvoicePaidEvent(UUID.randomUUID(), UUID.randomUUID(), "CASH"));
    }
}
