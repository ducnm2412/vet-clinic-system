package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.StaffNotificationFactory;
import com.vetclinic.notification.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Có đơn hàng chờ xác nhận: báo mọi nhân viên quầy, qua thông báo trong app (không email). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedStaffListener {

    private static final String STAFF_ROLE = "STAFF";

    private final NotificationService notificationService;
    private final UserDirectory userDirectory;

    @RabbitListener(queues = RabbitMQConfig.ORDER_PLACED_QUEUE)
    public void onOrderPlaced(OrderPlacedEvent event) {
        var content = StaffNotificationFactory.newOrder(event);
        var staff = userDirectory.idsByRole(STAFF_ROLE);
        notificationService.createForAll(staff, content.type(), content.title(), content.message(), content.link());
        log.info("Notified {} staff about new order {}", staff.size(), event.orderCode());
    }
}
