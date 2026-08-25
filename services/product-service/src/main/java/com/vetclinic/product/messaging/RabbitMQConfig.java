package com.vetclinic.product.messaging;

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

    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_COMPLETED_QUEUE = "product-service.order-completed";
    private static final String ROUTING_KEY_ORDER_COMPLETED = "order.completed";

    // Khai báo exchange ở phía consumer luôn: product-service có thể khởi động trước
    // order-service, lúc đó exchange chưa tồn tại thì binding sẽ lỗi. Khai báo hai đầu
    // là idempotent nên không xung đột khi order-service cũng khai báo y hệt.
    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCompletedQueue() {
        return new Queue(ORDER_COMPLETED_QUEUE, true);
    }

    @Bean
    public Binding orderCompletedBinding(Queue orderCompletedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderCompletedQueue).to(orderEventsExchange).with(ROUTING_KEY_ORDER_COMPLETED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
