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

    // Còn dùng cho appointment.created (notification-service nghe). prescription.created đã
    // chuyển sang exchange pet.events cùng với bệnh án (VD-10 chặng 2).
    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
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

    // CN-39: staff-service phát shift.added / shift.removed khi admin xếp hoặc bỏ ca trực.
    public static final String STAFF_EVENTS_EXCHANGE = "staff.events";
    public static final String STAFF_SHIFT_QUEUE = "booking.staff-shifts";

    @Bean
    public TopicExchange staffEventsExchange() {
        return new TopicExchange(STAFF_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue staffShiftQueue() {
        return new Queue(STAFF_SHIFT_QUEUE, true);
    }

    @Bean
    public Binding shiftAddedBinding(Queue staffShiftQueue, TopicExchange staffEventsExchange) {
        return BindingBuilder.bind(staffShiftQueue).to(staffEventsExchange).with("shift.added");
    }

    @Bean
    public Binding shiftRemovedBinding(Queue staffShiftQueue, TopicExchange staffEventsExchange) {
        return BindingBuilder.bind(staffShiftQueue).to(staffEventsExchange).with("shift.removed");
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
