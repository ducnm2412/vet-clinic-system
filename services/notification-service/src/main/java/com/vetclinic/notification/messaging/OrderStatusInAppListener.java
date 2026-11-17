package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.InAppNotificationFactory;
import com.vetclinic.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Báo khách trên chuông khi đơn hàng đổi trạng thái. Tách khỏi thư báo (hàng đợi riêng) để lỗi gửi
 * email không làm thông báo bị lưu trùng ở mỗi lần RabbitMQ thử lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusInAppListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_STATUS_INAPP_QUEUE)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        // Khách vừa tự bấm huỷ / "đã nhận hàng" thì không cần báo lại chính họ.
        if (event.byCustomer()) {
            return;
        }
        if (event.customerUserId() == null) {
            log.warn("Skip order notification {}: no customerUserId, order={}", event.status(), event.orderCode());
            return;
        }
        InAppNotificationFactory.forOrder(event).ifPresentOrElse(c -> {
            notificationService.create(event.customerUserId(), "ORDER_" + event.status(), c.title(), c.message(),
                    c.link());
            log.info("Notified customer {} about order {} → {}", event.customerUserId(), event.orderCode(),
                    event.status());
        }, () -> log.warn("Skip order notification: unknown status {}, order={}", event.status(),
                event.orderCode()));
    }
}
