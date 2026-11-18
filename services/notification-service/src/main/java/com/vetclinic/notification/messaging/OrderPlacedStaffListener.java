package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.NotificationService;
import com.vetclinic.notification.service.StaffNotificationFactory;
import com.vetclinic.notification.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Có đơn hàng chờ xác nhận, qua thông báo trong app (không email). Đơn đã được chia cho một nhân viên thì chỉ báo
 * người đó; chưa chia được (hôm đó không ai có ca) thì báo mọi nhân viên để đơn không bị bỏ sót. Admin luôn được báo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedStaffListener {

    private static final String STAFF_ROLE = "STAFF";
    private static final String ADMIN_ROLE = "ADMIN";

    private final NotificationService notificationService;
    private final UserDirectory userDirectory;

    @RabbitListener(queues = RabbitMQConfig.ORDER_PLACED_QUEUE)
    public void onOrderPlaced(OrderPlacedEvent event) {
        var content = StaffNotificationFactory.newOrder(event);
        if (event.assignedStaffId() != null) {
            notificationService.create(event.assignedStaffId(), content.type(), content.title(), content.message(),
                    content.link());
            log.info("Notified assigned staff {} about new order {}", event.assignedStaffId(), event.orderCode());
        } else {
            var staff = userDirectory.idsByRole(STAFF_ROLE);
            notificationService.createForAll(staff, content.type(), content.title(), content.message(), content.link());
            log.info("Notified {} staff about new order {}", staff.size(), event.orderCode());
        }
        notifyAdmins(event);
    }

    /**
     * Admin cũng được báo (kèm ai phụ trách) để theo dõi. Làm SAU phần nhân viên và nuốt lỗi: nếu tra danh sách
     * admin hỏng mà ném lỗi ra, RabbitMQ gửi lại cả sự kiện và nhân viên nhận thông báo lần hai.
     */
    private void notifyAdmins(OrderPlacedEvent event) {
        try {
            var content = StaffNotificationFactory.newOrderForAdmin(event);
            var admins = userDirectory.idsByRole(ADMIN_ROLE);
            notificationService.createForAll(admins, content.type(), content.title(), content.message(), content.link());
            log.info("Notified {} admin(s) about new order {}", admins.size(), event.orderCode());
        } catch (RuntimeException e) {
            log.warn("Could not notify admins about new order {}: {}", event.orderCode(), e.getMessage());
        }
    }
}
