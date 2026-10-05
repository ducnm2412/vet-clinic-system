package com.vetclinic.payment.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // pet-service là chủ khai báo gốc của exchange này — phải khớp chính xác durable/autoDelete
    // (true, false), khai lệch sẽ bị Rabbit throw PRECONDITION_FAILED khi redeclare một exchange
    // đã tồn tại với thuộc tính khác.
    //
    // Từ 25/09/2026 (VD-10 chặng 2): prescription.created do pet-service phát, không còn
    // booking-service — bệnh án đã chuyển sang đó. Nội dung message không đổi nên chỉ phải đổi chỗ
    // nghe. Binding cũ tới booking.events còn sót trong Rabbit là vô hại: không ai phát
    // prescription.created lên exchange đó nữa. Muốn dọn sạch thì xoá queue
    // payment.prescription-created rồi để service khai lại.
    public static final String PET_EVENTS_EXCHANGE = "pet.events";
    public static final String PRESCRIPTION_CREATED_QUEUE = "payment.prescription-created";
    private static final String ROUTING_KEY_PRESCRIPTION_CREATED = "prescription.created";

    // payment-service là chủ khai báo gốc của exchange này (pet-service redeclare lại y hệt
    // (true, false) để nhận payment.completed) — chỉ khai TopicExchange, không cần queue/binding
    // ở đây vì payment-service chỉ publish, không tự nghe lại message của chính mình.
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";

    @Bean
    public TopicExchange petEventsExchange() {
        return new TopicExchange(PET_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue prescriptionCreatedQueue() {
        return new Queue(PRESCRIPTION_CREATED_QUEUE, true);
    }

    @Bean
    public Binding prescriptionCreatedBinding(Queue prescriptionCreatedQueue, TopicExchange petEventsExchange) {
        return BindingBuilder.bind(prescriptionCreatedQueue).to(petEventsExchange)
                .with(ROUTING_KEY_PRESCRIPTION_CREATED);
    }

    // Bắt buộc phải có để @RabbitListener parse được JSON pet-service gửi thành record
    // Java (mặc định Spring Boot dùng SimpleMessageConverter, không hiểu JSON).
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
