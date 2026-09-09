package com.vetclinic.notification.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    // auth-service là chủ khai báo gốc của exchange này — phải khớp chính xác durable/autoDelete
    // (true, false), khai lệch sẽ bị Rabbit throw PRECONDITION_FAILED khi redeclare một exchange
    // đã tồn tại với thuộc tính khác.
    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_REGISTERED_QUEUE = "notification.user-registered";
    private static final String ROUTING_KEY_USER_REGISTERED = "user.registered";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(userEventsExchange).with(ROUTING_KEY_USER_REGISTERED);
    }

    // Bắt buộc phải có để @RabbitListener parse được JSON auth-service gửi thành record Java
    // (mặc định Spring Boot dùng SimpleMessageConverter, không hiểu JSON).
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // Không có DB nên không track "đã gửi chưa" — nếu để listener throw exception thẳng ra,
    // RabbitMQ sẽ requeue và gửi lại mail vô hạn cho cùng 1 user (ví dụ MailHog/SMTP tạm thời
    // sập). Ghi đè container factory mặc định: retry tối đa 3 lần (backoff 2s -> 4s) ngay trong
    // container, hết lượt thì log lỗi và ACK message (không rethrow, không requeue) — đúng tinh
    // thần "log warning, không throw" như markPrescriptionPaid bên booking-service, chỉ khác là
    // áp dụng ở tầng container vì đây là lỗi hạ tầng gửi mail, không phải lỗi dữ liệu nghiệp vụ.
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000L, 2.0, 10000L)
                .recoverer((message, cause) -> log.error(
                        "Giving up processing message after 3 attempts, message={}", message, cause))
                // MessageRecoverer.recover() trả về void và không rethrow -> container ACK
                // message, dừng requeue vô hạn.
                .build());
        return factory;
    }
}
