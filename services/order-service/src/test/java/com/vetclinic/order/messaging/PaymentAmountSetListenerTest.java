package com.vetclinic.order.messaging;

import com.vetclinic.order.service.ExamInvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentAmountSetListenerTest {

    @Mock private ExamInvoiceService examInvoiceService;
    @InjectMocks private PaymentAmountSetListener listener;

    @Test
    void onAmountSet_swallowsUnexpectedErrorsSoRabbitDoesNotRedeliverForever() {
        PaymentAmountSetEvent event = new PaymentAmountSetEvent(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000"));
        doThrow(new IllegalStateException("db down")).when(examInvoiceService).createFromAmountSet(any());

        listener.onAmountSet(event);

        verify(examInvoiceService).createFromAmountSet(event);
    }
}
