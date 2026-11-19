package com.vetclinic.staff.scheduler;

import com.vetclinic.staff.repository.AttendanceRecordRepository;
import com.vetclinic.staff.service.AttendanceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttendanceAutoCloseJobTest {

    private final AttendanceRecordRepository repository = mock(AttendanceRecordRepository.class);
    private final AttendanceService service = mock(AttendanceService.class);

    @Test
    void closesEveryOpenRecord_andOneFailureDoesNotStopTheRest() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(repository.findOpenIdsBefore(any(), any())).thenReturn(List.of(a, b, c));
        when(service.autoClose(b)).thenThrow(new RuntimeException("boom"));

        new AttendanceAutoCloseJob(repository, service, true).run();

        verify(service).autoClose(a);
        verify(service).autoClose(b);
        verify(service).autoClose(c);
    }

    @Test
    void disabledJobDoesNothing() {
        new AttendanceAutoCloseJob(repository, service, false).run();

        verifyNoInteractions(repository, service);
    }

    @Test
    void nothingOpen_nothingClosed() {
        when(repository.findOpenIdsBefore(any(), any())).thenReturn(List.of());

        new AttendanceAutoCloseJob(repository, service, true).run();

        verify(service, never()).autoClose(any());
    }
}
