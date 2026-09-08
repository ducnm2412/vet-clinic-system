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

    // booking-service là chủ khai báo gốc của exchange này — phải khớp chính xác
    // durable/autoDelete (true, false), khai lệch sẽ bị Rabbit throw PRECONDITION_FAILED
    // khi redeclare một exchange đã tồn tại với thuộc tính khác.
    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";
    public static final String PRESCRIPTION_CREATED_QUEUE = "payment.prescription-created";
    private static final String ROUTING_KEY_PRESCRIPTION_CREATED = "prescription.created";

    // payment-service là chủ khai báo gốc của exchange này (booking-service redeclare lại y hệt
    // (true, false) để nhận payment.completed) — chỉ khai TopicExchange, không cần queue/binding
    // ở đây vì payment-service chỉ publish, không tự nghe lại message của chính mình.
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
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
    public Binding prescriptionCreatedBinding(Queue prescriptionCreatedQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(prescriptionCreatedQueue).to(bookingEventsExchange)
                .with(ROUTING_KEY_PRESCRIPTION_CREATED);
    }

    // Bắt buộc phải có để @RabbitListener parse được JSON booking-service gửi thành record
    // Java (mặc định Spring Boot dùng SimpleMessageConverter, không hiểu JSON).
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
