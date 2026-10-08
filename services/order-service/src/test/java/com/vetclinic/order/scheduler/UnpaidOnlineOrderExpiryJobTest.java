package com.vetclinic.order.scheduler;

import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.service.ExpiredOnlineOrderProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnpaidOnlineOrderExpiryJobTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ExpiredOnlineOrderProcessor processor;

    @Test
    void run_cancelsEachExpiredOrder() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(orderRepository.findExpiredUnpaidOnlineOrderIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(a, b));
        when(processor.process(any())).thenReturn(true);

        new UnpaidOnlineOrderExpiryJob(orderRepository, processor).run();

        verify(processor).process(a);
        verify(processor).process(b);
    }

    @Test
    void run_oneFailingOrderDoesNotBlockTheOthers() {
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(orderRepository.findExpiredUnpaidOnlineOrderIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(bad, good));
        when(processor.process(bad)).thenThrow(new IllegalStateException("db down"));
        when(processor.process(good)).thenReturn(true);

        new UnpaidOnlineOrderExpiryJob(orderRepository, processor).run();

        verify(processor).process(good);
    }

    @Test
    void run_withNothingExpired_doesNothing() {
        when(orderRepository.findExpiredUnpaidOnlineOrderIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        new UnpaidOnlineOrderExpiryJob(orderRepository, processor).run();

        verify(processor, never()).process(any());
    }

    @Test
    void run_limitsBatchSize() {
        when(orderRepository.findExpiredUnpaidOnlineOrderIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        new UnpaidOnlineOrderExpiryJob(orderRepository, processor).run();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findExpiredUnpaidOnlineOrderIds(any(Instant.class), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(UnpaidOnlineOrderExpiryJob.BATCH_SIZE);
    }

    @Test
    void run_onlyPicksOrdersExpiredLongerThanTheGraceWindow() {
        when(orderRepository.findExpiredUnpaidOnlineOrderIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());
        Instant before = Instant.now();

        new UnpaidOnlineOrderExpiryJob(orderRepository, processor).run();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findExpiredUnpaidOnlineOrderIds(cutoff.capture(), any(Pageable.class));
        // Mốc lọc phải lùi lại đúng GRACE so với lúc chạy, để giao dịch sát giờ có thời gian được cổng chốt.
        assertThat(cutoff.getValue()).isBefore(before.minus(UnpaidOnlineOrderExpiryJob.GRACE).plusSeconds(1));
    }
}
