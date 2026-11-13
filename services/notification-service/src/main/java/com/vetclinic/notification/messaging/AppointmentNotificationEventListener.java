package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import com.vetclinic.notification.service.InAppNotificationFactory;
import com.vetclinic.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Báo khách về lịch đặt theo yêu cầu (đã nhận, đã xác nhận, đề xuất giờ khác, bị huỷ) qua hai kênh:
 * thông báo trong app (chuông) và email. Hai kênh độc lập: thiếu email vẫn có thông báo trong app,
 * thiếu userId (booking-service bản cũ) vẫn gửi được email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNotificationEventListener {

    private final EmailService emailService;
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.APPOINTMENT_NOTIFICATION_QUEUE)
    public void onAppointmentNotification(AppointmentNotificationEvent event) {
        // Lưu thông báo trong app trước: ghi database nhanh và ít khi lỗi. Gửi email lỗi thì RabbitMQ
        // thử lại cả message, nên làm email trước sẽ lưu trùng thông báo ở mỗi lần thử.
        boolean known = saveInApp(event);

        // Không có địa chỉ thì không có gì để gửi. Bỏ qua chứ không ném lỗi — ném lỗi chỉ khiến
        // RabbitMQ thử lại vô ích.
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("Skip appointment notification email {}: no customer email, appointmentId={}", event.type(),
                    event.appointmentId());
            return;
        }
        if (!emailService.sendAppointmentNotification(event)) {
            if (!known) {
                log.warn("Skip appointment notification: unknown type {}, appointmentId={}", event.type(),
                        event.appointmentId());
            }
            return;
        }
        log.info("Processed appointment.notification {} for appointmentId={}", event.type(), event.appointmentId());
    }

    /** Trả {@code true} nếu loại sự kiện này biết được (kể cả khi không có người nhận để lưu). */
    private boolean saveInApp(AppointmentNotificationEvent event) {
        var content = InAppNotificationFactory.forAppointment(event);
        if (content.isEmpty()) {
            return false;
        }
        if (event.customerUserId() == null) {
            log.warn("Skip in-app notification {}: no customerUserId, appointmentId={}", event.type(),
                    event.appointmentId());
            return true;
        }
        notificationService.create(event.customerUserId(), event.type(), content.get().title(),
                content.get().message(), content.get().link());
        return true;
    }
}
