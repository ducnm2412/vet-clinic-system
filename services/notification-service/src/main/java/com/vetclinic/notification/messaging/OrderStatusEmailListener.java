package com.vetclinic.notification.messaging;

import com.vetclinic.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Gửi thư báo khách khi đơn đang giao, đã giao hoặc bị huỷ (các bước còn lại chỉ báo trên chuông). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusEmailListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_STATUS_EMAIL_QUEUE)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.byCustomer()) {
            return;
        }
        // Đơn cũ và đơn đặt trước khi có tính năng này không có email: bỏ qua chứ không ném lỗi,
        // ném lỗi chỉ khiến RabbitMQ thử lại vô ích.
        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("Skip order email {}: no customer email, order={}", event.status(), event.orderCode());
            return;
        }
        if (emailService.sendOrderStatus(event)) {
            log.info("Sent order email {} for order {}", event.status(), event.orderCode());
        }
    }
}
