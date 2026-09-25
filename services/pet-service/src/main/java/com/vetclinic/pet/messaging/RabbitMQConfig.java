package com.vetclinic.pet.messaging;

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

    /** Exchange của service này: hiện phát prescription.created. */
    public static final String PET_EVENTS_EXCHANGE = "pet.events";

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_DELETED_QUEUE = "pet-service.user-deleted";
    private static final String ROUTING_KEY_USER_DELETED = "user.deleted";

    // Khai cả exchange của payment-service (idempotent, ai khai trước cũng được) để queue nghe
    // payment.completed có chỗ bám dù pet-service lên trước.
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String PAYMENT_COMPLETED_QUEUE = "pet.payment-completed";
    private static final String ROUTING_KEY_PAYMENT_COMPLETED = "payment.completed";

    @Bean
    public TopicExchange petEventsExchange() {
        return new TopicExchange(PET_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue userDeletedQueue() {
        return new Queue(USER_DELETED_QUEUE, true);
    }

    @Bean
    public Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userDeletedQueue).to(userEventsExchange).with(ROUTING_KEY_USER_DELETED);
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
