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
    public static final String PASSWORD_RESET_REQUESTED_QUEUE = "notification.password-reset-requested";
    private static final String ROUTING_KEY_PASSWORD_RESET_REQUESTED = "user.password-reset-requested";

    // booking-service là chủ khai báo gốc — thuộc tính (durable=true, autoDelete=false) phải khớp.
    public static final String BOOKING_EVENTS_EXCHANGE = "booking.events";
    public static final String APPOINTMENT_CREATED_QUEUE = "notification.appointment-created";
    private static final String ROUTING_KEY_APPOINTMENT_CREATED = "appointment.created";
    public static final String APPOINTMENT_NOTIFICATION_QUEUE = "notification.appointment-notification";
    private static final String ROUTING_KEY_APPOINTMENT_NOTIFICATION = "appointment.notification";

    // Thông báo cho phía phòng khám nghe CÙNG routing key với thư báo khách nhưng bằng hàng đợi riêng:
    // hai việc thử lại độc lập, lỗi gửi email không làm thông báo nhân viên bị lưu trùng.
    public static final String APPOINTMENT_STAFF_NOTICE_QUEUE = "notification.appointment-staff-notice";

    // pet-service là chủ khai báo gốc (durable=true, autoDelete=false).
    public static final String PET_EVENTS_EXCHANGE = "pet.events";
    public static final String PRESCRIPTION_CREATED_QUEUE = "notification.prescription-created";
    private static final String ROUTING_KEY_PRESCRIPTION_CREATED = "prescription.created";

    // order-service là chủ khai báo gốc (durable=true, autoDelete=false).
    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String ORDER_PLACED_QUEUE = "notification.order-placed";
    private static final String ROUTING_KEY_ORDER_PLACED = "order.placed";

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPlacedQueue() {
        return new Queue(ORDER_PLACED_QUEUE, true);
    }

    @Bean
    public Binding orderPlacedBinding(Queue orderPlacedQueue, TopicExchange orderEventsExchange) {
        return BindingBuilder.bind(orderPlacedQueue).to(orderEventsExchange).with(ROUTING_KEY_ORDER_PLACED);
    }

    @Bean
    public Queue appointmentStaffNoticeQueue() {
        return new Queue(APPOINTMENT_STAFF_NOTICE_QUEUE, true);
    }

    @Bean
    public Binding appointmentStaffNoticeBinding(Queue appointmentStaffNoticeQueue,
                                                 TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(appointmentStaffNoticeQueue).to(bookingEventsExchange)
                .with(ROUTING_KEY_APPOINTMENT_NOTIFICATION);
    }

    @Bean
    public TopicExchange petEventsExchange() {
        return new TopicExchange(PET_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue prescriptionCreatedQueue() {
        return new Queue(PRESCRIPTION_CREATED_QUEUE, true);
    }

    @Bean
    public Binding prescriptionCreatedBinding(Queue prescriptionCreatedQueue, TopicExchange petEventsExchange) {
        return BindingBuilder.bind(prescriptionCreatedQueue).to(petEventsExchange)
                .with(ROUTING_KEY_PRESCRIPTION_CREATED);
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    public static final String STAFF_CREATED_QUEUE = "notification.staff-created";
    private static final String ROUTING_KEY_STAFF_CREATED = "user.staff-created";

    @Bean
    public Queue staffCreatedQueue() {
        return new Queue(STAFF_CREATED_QUEUE, true);
    }

    @Bean
    public Binding staffCreatedBinding(Queue staffCreatedQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(staffCreatedQueue).to(userEventsExchange).with(ROUTING_KEY_STAFF_CREATED);
    }

    public static final String PROFILE_UPDATED_QUEUE = "notification.profile-updated";
    private static final String ROUTING_KEY_PROFILE_UPDATED = "profile.updated";

    @Bean
    public Queue profileUpdatedQueue() {
        return new Queue(PROFILE_UPDATED_QUEUE, true);
    }

    @Bean
    public Binding profileUpdatedBinding(Queue profileUpdatedQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(profileUpdatedQueue).to(userEventsExchange).with(ROUTING_KEY_PROFILE_UPDATED);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(userEventsExchange).with(ROUTING_KEY_USER_REGISTERED);
    }

    @Bean
    public Queue passwordResetRequestedQueue() {
        return new Queue(PASSWORD_RESET_REQUESTED_QUEUE, true);
    }

    @Bean
    public Binding passwordResetRequestedBinding(Queue passwordResetRequestedQueue,
                                                 TopicExchange userEventsExchange) {
        return BindingBuilder.bind(passwordResetRequestedQueue).to(userEventsExchange)
                .with(ROUTING_KEY_PASSWORD_RESET_REQUESTED);
    }

    @Bean
    public TopicExchange bookingEventsExchange() {
        return new TopicExchange(BOOKING_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue appointmentCreatedQueue() {
        return new Queue(APPOINTMENT_CREATED_QUEUE, true);
    }

    @Bean
    public Binding appointmentCreatedBinding(Queue appointmentCreatedQueue, TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(appointmentCreatedQueue).to(bookingEventsExchange)
                .with(ROUTING_KEY_APPOINTMENT_CREATED);
    }

    @Bean
    public Queue appointmentNotificationQueue() {
        return new Queue(APPOINTMENT_NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding appointmentNotificationBinding(Queue appointmentNotificationQueue,
                                                  TopicExchange bookingEventsExchange) {
        return BindingBuilder.bind(appointmentNotificationQueue).to(bookingEventsExchange)
                .with(ROUTING_KEY_APPOINTMENT_NOTIFICATION);
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
