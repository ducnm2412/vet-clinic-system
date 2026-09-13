package com.vetclinic.booking.service;

import com.vetclinic.booking.dto.AppointmentStatsResponse;
import com.vetclinic.booking.dto.AppointmentStatsResponse.ByDoctor;
import com.vetclinic.booking.dto.AppointmentStatsResponse.Counts;
import com.vetclinic.booking.dto.AppointmentStatsResponse.Daily;
import com.vetclinic.booking.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Test thuần Mockito, không chạm database — xem VD-12 về test xoá dữ liệu thật.
@ExtendWith(MockitoExtension.class)
class AppointmentStatsServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Mock private AppointmentRepository appointmentRepository;
    @InjectMocks private AppointmentStatsService service;

    @Test
    void mapsCountsSortedByDayAndDoctorsBusiestFirst() {
        UUID quiet = UUID.randomUUID();
        UUID busy = UUID.randomUUID();
        when(appointmentRepository.countByDayAndStatus(D1, D2)).thenReturn(List.of(
                new Object[]{D2, 2L, 0L, 1L, 1L, 0L, 0L},
                new Object[]{java.sql.Date.valueOf(D1), 3L, 1L, 0L, 1L, 1L, 0L}));
        when(appointmentRepository.countByDoctorAndStatus(D1, D2)).thenReturn(List.of(
                new Object[]{quiet, 1L, 0L, 0L, 1L, 0L, 0L},
                new Object[]{busy.toString(), 4L, 1L, 1L, 1L, 1L, 0L}));

        AppointmentStatsResponse result = service.stats(D1, D2);

        assertThat(result.daily()).containsExactly(
                new Daily(D1, new Counts(3, 1, 0, 1, 1, 0)),
                new Daily(D2, new Counts(2, 0, 1, 1, 0, 0)));
        assertThat(result.byDoctor()).extracting(ByDoctor::doctorUserId).containsExactly(busy, quiet);
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> service.stats(D2, D1)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(appointmentRepository);
    }
}
