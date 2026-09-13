package com.vetclinic.payment.service;

import com.vetclinic.payment.dto.PaymentDailyStatsResponse;
import com.vetclinic.payment.repository.PaymentRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentStatsServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @InjectMocks private PaymentStatsService service;

    @Test
    void mapsRowsSortedByDayUsingVietnamDayBoundaries() {
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 2);
        // 00:00 ngày 1 giờ VN = 17:00 ngày 31/8 UTC; hết ngày 2 = 17:00 ngày 2 UTC.
        when(paymentRepository.sumCompletedPerDay(Instant.parse("2026-08-31T17:00:00Z"), Instant.parse("2026-09-02T17:00:00Z")))
                .thenReturn(List.of(
                        new Object[]{d2, 1L, new BigDecimal("300000.00")},
                        new Object[]{java.sql.Date.valueOf(d1), java.math.BigInteger.TWO, new BigDecimal("500000.00")}));

        assertThat(service.daily(d1, d2)).containsExactly(
                new PaymentDailyStatsResponse(d1, 2, new BigDecimal("500000.00")),
                new PaymentDailyStatsResponse(d2, 1, new BigDecimal("300000.00")));
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> service.daily(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(paymentRepository);
    }
}
