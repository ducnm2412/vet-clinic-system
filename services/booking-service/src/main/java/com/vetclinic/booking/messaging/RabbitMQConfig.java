package com.vetclinic.booking.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";

    // payment-service chưa tồn tại — booking-service tự khai báo exchange này (idempotent, ai
    // khai báo trước cũng được) để đã có sẵn queue lắng nghe payment.completed ngay từ bây giờ.
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String PAYMENT_COMPLETED_QUEUE = "booking.payment-completed";
    private static final String ROUTING_KEY_PAYMENT_COMPLETED = "payment.completed";

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return new Queue(PAYMENT_COMPLETED_QUEUE, true);
    }

    @Bean
    public Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentEventsExchange).with(ROUTING_KEY_PAYMENT_COMPLETED);
    }

    // CN-08: auth-service phát user.locked / user.unlocked. Khai exchange ở cả hai bên (idempotent).
    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_STATUS_QUEUE = "booking.user-status-changed";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue userStatusQueue() {
        return new Queue(USER_STATUS_QUEUE, true);
    }

    @Bean
    public Binding userLockedBinding(Queue userStatusQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userStatusQueue).to(userEventsExchange).with("user.locked");
    }

    @Bean
    public Binding userUnlockedBinding(Queue userStatusQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userStatusQueue).to(userEventsExchange).with("user.unlocked");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
