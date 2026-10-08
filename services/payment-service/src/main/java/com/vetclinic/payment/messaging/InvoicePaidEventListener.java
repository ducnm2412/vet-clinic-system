package com.vetclinic.payment.messaging;

import com.vetclinic.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoicePaidEventListener {

    private final PaymentService paymentService;

    @RabbitListener(queues = RabbitMQConfig.INVOICE_PAID_QUEUE)
    public void onInvoicePaid(InvoicePaidEvent event) {
        paymentService.completeFromInvoice(event.examPaymentId(), event.method(), event.orderId());
    }
}
