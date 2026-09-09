package com.vetclinic.payment.messaging;

import com.vetclinic.payment.domain.Payment;
import com.vetclinic.payment.domain.PaymentStatus;
import com.vetclinic.payment.repository.PaymentRepository;
import com.vetclinic.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// KHÔNG @Transactional ở đây — @TransactionalEventListener(AFTER_COMMIT) chỉ bắn event khi
// transaction confirmCash() THẬT SỰ commit (giống AppointmentEventPublisherTest bên booking-service).
@SpringBootTest(properties = "eureka.client.enabled=false")
class PaymentEventPublisherTest {

    private static final String TEST_QUEUE = "test.payment-completed";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void cleanup() {
        amqpAdmin.deleteQueue(TEST_QUEUE);
        paymentRepository.deleteAll();
    }

    @Test
    void confirmCash_afterCommit_publishesPaymentCompletedEventToRabbitMQ() {
        // autoDelete=false: test thứ 2 gọi receiveAndConvert 2 lần trên cùng 1 queue — nếu
        // autoDelete=true, Rabbit sẽ tự xoá queue ngay sau lần receive đầu tiên (consumer tạm
        // của receiveAndConvert ngắt kết nối = "consumer cuối cùng" rời khỏi queue).
        Queue queue = new Queue(TEST_QUEUE, false, false, false);
        TopicExchange exchange = new TopicExchange(RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE, true, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("payment.completed");
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        UUID medicalRecordId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .medicalRecordId(medicalRecordId)
                .appointmentId(appointmentId)
                .customerUserId(UUID.randomUUID())
                .status(PaymentStatus.PENDING_PAYMENT)
                .amount(new BigDecimal("150000"))
                .build());

        paymentService.confirmCash(payment.getId());

        PaymentCompletedEvent event = (PaymentCompletedEvent) rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000);

        assertThat(event).isNotNull();
        assertThat(event.paymentId()).isEqualTo(payment.getId());
        assertThat(event.medicalRecordId()).isEqualTo(medicalRecordId);
        assertThat(event.appointmentId()).isEqualTo(appointmentId);
    }

    @Test
    void confirmCash_calledAgainWhenAlreadyCompleted_doesNotPublishDuplicateEvent() {
        // autoDelete=false: test thứ 2 gọi receiveAndConvert 2 lần trên cùng 1 queue — nếu
        // autoDelete=true, Rabbit sẽ tự xoá queue ngay sau lần receive đầu tiên (consumer tạm
        // của receiveAndConvert ngắt kết nối = "consumer cuối cùng" rời khỏi queue).
        Queue queue = new Queue(TEST_QUEUE, false, false, false);
        TopicExchange exchange = new TopicExchange(RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE, true, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("payment.completed");
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .medicalRecordId(UUID.randomUUID())
                .appointmentId(UUID.randomUUID())
                .customerUserId(UUID.randomUUID())
                .status(PaymentStatus.PENDING_PAYMENT)
                .amount(new BigDecimal("150000"))
                .build());

        paymentService.confirmCash(payment.getId());
        assertThat(rabbitTemplate.receiveAndConvert(TEST_QUEUE, 5000)).isNotNull();

        // Gọi lại lần 2 khi đã COMPLETED — không được publish thêm event nữa, tránh spam
        // booking-service mỗi lần client retry API.
        paymentService.confirmCash(payment.getId());
        assertThat(rabbitTemplate.receiveAndConvert(TEST_QUEUE, 2000)).isNull();
    }
}
