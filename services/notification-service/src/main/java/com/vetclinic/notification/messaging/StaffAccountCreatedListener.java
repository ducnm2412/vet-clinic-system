package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.StaffNotificationFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Admin vừa tạo tài khoản bác sĩ hoặc nhân viên: để sẵn một thông báo nhắc họ cập nhật hồ sơ. Lưu
 * ngay lúc tạo tài khoản — người đó chưa đăng nhập lần nào nên chưa thể xem; lần đăng nhập đầu tiên
 * chuông đã có chấm đỏ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffAccountCreatedListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.STAFF_CREATED_QUEUE)
    public void onStaffAccountCreated(StaffAccountCreatedEvent event) {
        if (event.userId() == null) {
            log.warn("Skip profile-setup notification: no userId");
            return;
        }
        StaffNotificationFactory.profileSetup(event.role()).ifPresent(c -> {
            // Một lần trong đời: RabbitMQ giao lại sự kiện thì không nhắc hai lần.
            if (notificationService.createOnce(event.userId(), c.type(), c.title(), c.message(), c.link())) {
                log.info("Saved profile-setup notification for userId={} role={}", event.userId(), event.role());
            }
        });
    }
}
