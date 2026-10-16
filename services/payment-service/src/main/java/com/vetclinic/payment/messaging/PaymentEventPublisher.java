package com.vetclinic.payment.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private static final String ROUTING_KEY_PAYMENT_COMPLETED = "payment.completed";
    private static final String ROUTING_KEY_AMOUNT_SET = "payment.amount-set";

    private final RabbitTemplate rabbitTemplate;

    // AFTER_COMMIT: chỉ publish khi transaction confirmCash() đã commit thành công (giống
    // UserEventPublisher/PrescriptionEventPublisher) — tránh báo pet-service là đã thanh
    // toán trong khi DB thực ra rollback.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE, ROUTING_KEY_PAYMENT_COMPLETED, event);
        log.info("Published payment.completed event for paymentId={}, medicalRecordId={}",
                event.paymentId(), event.medicalRecordId());
    }

    // AFTER_COMMIT vì cùng lý do: chỉ báo order-service dựng hoá đơn chờ khi số tiền đã thật sự được lưu.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAmountSet(PaymentAmountSetEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE, ROUTING_KEY_AMOUNT_SET, event);
        log.info("Published payment.amount-set event for paymentId={}, amount={}", event.paymentId(), event.amount());
    }
}
