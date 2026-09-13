package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** CN-43 — gửi email xác nhận khi khách đặt lịch khám. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentCreatedEventListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_CREATED_QUEUE)
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        // Lịch do nhân viên tạo hộ, hoặc message từ bản booking-service cũ chưa gửi email,
        // thì không có địa chỉ để gửi. Bỏ qua chứ không ném lỗi — ném lỗi chỉ khiến RabbitMQ
        // thử lại vô ích.
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("Skip appointment confirmation: no customer email, appointmentId={}", event.appointmentId());
            return;
        }
        emailService.sendAppointmentConfirmation(event);
        log.info("Processed appointment.created event for appointmentId={}", event.appointmentId());
    }
}
