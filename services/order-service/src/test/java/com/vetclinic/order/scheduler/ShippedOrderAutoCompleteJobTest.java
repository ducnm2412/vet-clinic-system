package com.vetclinic.order.scheduler;

import com.vetclinic.order.repository.OrderRepository;
import com.vetclinic.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippedOrderAutoCompleteJobTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    @Test
    void run_completesEachOrderShippedLongEnoughAgo() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(orderRepository.findShippingOrderIdsShippedBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(a, b));
        when(orderService.autoCompleteShippedOrder(any(), anyInt())).thenReturn(true);
        Instant before = Instant.now();

        new ShippedOrderAutoCompleteJob(orderRepository, orderService, 7).run();

        verify(orderService).autoCompleteShippedOrder(a, 7);
        verify(orderService).autoCompleteShippedOrder(b, 7);
        // Mốc cắt = bây giờ trừ 7 ngày.
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findShippingOrderIdsShippedBefore(cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofDays(7)),
                Instant.now().minus(Duration.ofDays(7)));
    }

    @Test
    void run_oneFailingOrderDoesNotBlockTheOthers() {
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(orderRepository.findShippingOrderIdsShippedBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(bad, good));
        when(orderService.autoCompleteShippedOrder(bad, 7)).thenThrow(new IllegalStateException("db down"));
        when(orderService.autoCompleteShippedOrder(good, 7)).thenReturn(true);

        new ShippedOrderAutoCompleteJob(orderRepository, orderService, 7).run();

        verify(orderService).autoCompleteShippedOrder(good, 7);
    }

    @Test
    void run_isOffWhenConfiguredWithZeroDays() {
        new ShippedOrderAutoCompleteJob(orderRepository, orderService, 0).run();

        verifyNoInteractions(orderRepository, orderService);
    }

    @Test
    void run_withNothingToComplete_doesNothing() {
        when(orderRepository.findShippingOrderIdsShippedBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        new ShippedOrderAutoCompleteJob(orderRepository, orderService, 7).run();

        verify(orderService, never()).autoCompleteShippedOrder(any(), anyInt());
    }
}
