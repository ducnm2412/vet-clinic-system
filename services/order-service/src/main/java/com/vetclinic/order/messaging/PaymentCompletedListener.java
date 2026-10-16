package com.vetclinic.order.messaging;

import com.vetclinic.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Nếu khoản khám được thu bằng confirm-cash (không qua hoá đơn quầy) thì huỷ hoá đơn chờ,
 * tránh để lại hoá đơn mồ côi. Thu qua collect thì hoá đơn đã COMPLETED — listener bỏ qua.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            orderService.cancelPendingExamInvoiceAfterDirectPayment(event == null ? null : event.paymentId());
        } catch (RuntimeException e) {
            log.error("Không xử lý được payment.completed cho khoản khám {}",
                    event == null ? null : event.paymentId(), e);
        }
    }
}
