package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Thư báo khách về lịch đặt theo yêu cầu: đã nhận, đã xác nhận, đề xuất giờ khác, bị huỷ. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNotificationEventListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_NOTIFICATION_QUEUE)
    public void onAppointmentNotification(AppointmentNotificationEvent event) {
        // Không có địa chỉ thì không có gì để gửi. Bỏ qua chứ không ném lỗi — ném lỗi chỉ khiến
        // RabbitMQ thử lại vô ích.
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("Skip appointment notification {}: no customer email, appointmentId={}", event.type(),
                    event.appointmentId());
            return;
        }
        if (!emailService.sendAppointmentNotification(event)) {
            log.warn("Skip appointment notification: unknown type {}, appointmentId={}", event.type(),
                    event.appointmentId());
            return;
        }
        log.info("Processed appointment.notification {} for appointmentId={}", event.type(), event.appointmentId());
    }
}
