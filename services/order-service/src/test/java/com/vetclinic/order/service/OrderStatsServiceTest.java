package com.vetclinic.order.service;

import com.vetclinic.order.dto.OrderDailyStatsResponse;
import com.vetclinic.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatsServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Mock private OrderRepository orderRepository;
    @InjectMocks private OrderStatsService service;

    @Test
    void mergesThreeQueriesIntoOneRowPerDay() {
        when(orderRepository.countCreatedPerDay(any(), any())).thenReturn(rows(new Object[]{java.sql.Date.valueOf(D1), 3L}));
        when(orderRepository.sumCompletedPerDay(any(), any())).thenReturn(rows(
                new Object[]{java.sql.Date.valueOf(D1), 1L, new BigDecimal("150000.00")},
                new Object[]{D2, 2L, new BigDecimal("420000.00")}));
        when(orderRepository.countCancelledPerDay(any(), any())).thenReturn(rows(new Object[]{D2, java.math.BigInteger.ONE}));

        List<OrderDailyStatsResponse> result = service.daily(D1, D2);

        assertThat(result).containsExactly(
                new OrderDailyStatsResponse(D1, 3, 1, 0, new BigDecimal("150000.00")),
                new OrderDailyStatsResponse(D2, 0, 2, 1, new BigDecimal("420000.00")));
    }

    @Test
    void rangeCoversWholeDaysInVietnamTime() {
        when(orderRepository.countCreatedPerDay(any(), any())).thenReturn(List.of());
        when(orderRepository.sumCompletedPerDay(any(), any())).thenReturn(List.of());
        when(orderRepository.countCancelledPerDay(any(), any())).thenReturn(List.of());

        service.daily(D1, D2);

        // 00:00 ngày 1 giờ VN = 17:00 ngày 31/8 UTC; hết ngày 2 = 17:00 ngày 2 UTC.
        verify(orderRepository).countCreatedPerDay(Instant.parse("2026-08-31T17:00:00Z"), Instant.parse("2026-09-02T17:00:00Z"));
    }

    @Test
    void rejectsReversedOrTooLongRange() {
        assertThatThrownBy(() -> service.daily(D2, D1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.daily(D1, D1.plusDays(OrderStatsService.MAX_DAYS)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(orderRepository);
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }
}
