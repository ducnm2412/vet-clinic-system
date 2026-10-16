package com.vetclinic.order.messaging;

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
    public static final String ROUTING_KEY_ORDER_COMPLETED = "order.completed";
    public static final String ROUTING_KEY_ORDER_CANCELLED = "order.cancelled";
    public static final String ROUTING_KEY_INVOICE_PAID = "order.invoice-paid";

    // payment-service là chủ gốc của exchange này; khai y hệt (true, false) để nhận payment.amount-set.
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String PAYMENT_AMOUNT_SET_QUEUE = "order.payment-amount-set";
    public static final String PAYMENT_COMPLETED_QUEUE = "order.payment-completed";
    private static final String ROUTING_KEY_PAYMENT_AMOUNT_SET = "payment.amount-set";
    private static final String ROUTING_KEY_PAYMENT_COMPLETED = "payment.completed";

    // Khai báo idempotent: product-service cũng khai y hệt exchange này ở phía consumer,
    // service nào khởi động trước cũng không sao.
    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentAmountSetQueue() {
        return new Queue(PAYMENT_AMOUNT_SET_QUEUE, true);
    }

    @Bean
    public Binding paymentAmountSetBinding(Queue paymentAmountSetQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentAmountSetQueue).to(paymentEventsExchange)
                .with(ROUTING_KEY_PAYMENT_AMOUNT_SET);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return new Queue(PAYMENT_COMPLETED_QUEUE, true);
    }

    @Bean
    public Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentEventsExchange)
                .with(ROUTING_KEY_PAYMENT_COMPLETED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
