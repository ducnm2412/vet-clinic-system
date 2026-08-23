package com.vetclinic.profile.messaging;

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

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_DELETED_QUEUE = "profile-service.user-deleted";
    private static final String ROUTING_KEY_USER_DELETED = "user.deleted";

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
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
