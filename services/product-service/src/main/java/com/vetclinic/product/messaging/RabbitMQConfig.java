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
    public static final String ORDER_CANCELLED_QUEUE = "product-service.order-cancelled";
    // payment-service là chủ khai báo gốc của exchange này; khai y hệt (true, false) ở phía nghe.
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String PRESCRIPTION_DISPENSED_QUEUE = "product-service.prescription-dispensed";
    private static final String ROUTING_KEY_PRESCRIPTION_DISPENSED = "payment.dispensed";
    private static final String ROUTING_KEY_ORDER_COMPLETED = "order.completed";
    private static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";

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
    public Queue orderCancelledQueue() {
        return new Queue(ORDER_CANCELLED_QUEUE, true);
    }

    @Bean
    public Binding orderCancelledBinding(Queue orderCancelledQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(orderEventsExchange).with(ROUTING_KEY_ORDER_CANCELLED);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue prescriptionDispensedQueue() {
        return new Queue(PRESCRIPTION_DISPENSED_QUEUE, true);
    }

    @Bean
    public Binding prescriptionDispensedBinding(Queue prescriptionDispensedQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(prescriptionDispensedQueue).to(paymentEventsExchange)
                .with(ROUTING_KEY_PRESCRIPTION_DISPENSED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
