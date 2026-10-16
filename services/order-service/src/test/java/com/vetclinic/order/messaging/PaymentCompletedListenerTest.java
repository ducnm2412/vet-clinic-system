package com.vetclinic.order.messaging;

import com.vetclinic.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedListenerTest {

    @Mock private OrderService orderService;
    @InjectMocks private PaymentCompletedListener listener;

    @Test
    void onPaymentCompleted_swallowsUnexpectedErrorsSoRabbitDoesNotRedeliverForever() {
        UUID paymentId = UUID.randomUUID();
        doThrow(new IllegalStateException("db down")).when(orderService)
                .cancelPendingExamInvoiceAfterDirectPayment(any());

        listener.onPaymentCompleted(new PaymentCompletedEvent(paymentId, UUID.randomUUID(), UUID.randomUUID()));

        verify(orderService).cancelPendingExamInvoiceAfterDirectPayment(paymentId);
    }
}
