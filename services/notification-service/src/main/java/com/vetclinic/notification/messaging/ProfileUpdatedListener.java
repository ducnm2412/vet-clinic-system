package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.StaffNotificationFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Người dùng đã lưu hồ sơ: gỡ thông báo nhắc cập nhật hồ sơ (nếu còn). Gọi lại nhiều lần vẫn an toàn. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileUpdatedListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.PROFILE_UPDATED_QUEUE)
    public void onProfileUpdated(ProfileUpdatedEvent event) {
        if (event.userId() == null) {
            return;
        }
        int removed = notificationService.removeByType(event.userId(), StaffNotificationFactory.PROFILE_SETUP);
        if (removed > 0) {
            log.info("Removed profile-setup reminder for userId={}", event.userId());
        }
    }
}
