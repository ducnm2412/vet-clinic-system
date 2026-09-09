package com.vetclinic.order.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ROUTING_KEY_ORDER_COMPLETED = "order.completed";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";

    // Khai báo idempotent: product-service cũng khai y hệt exchange này ở phía consumer,
    // service nào khởi động trước cũng không sao.
    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
