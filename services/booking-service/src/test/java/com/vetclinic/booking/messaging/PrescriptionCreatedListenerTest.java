package com.vetclinic.booking.messaging;

import com.vetclinic.booking.service.AppointmentService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class PrescriptionCreatedListenerTest {

    private final AppointmentService appointmentService = mock(AppointmentService.class);
    private final PrescriptionCreatedListener listener = new PrescriptionCreatedListener(appointmentService);

    @Test
    void completesTheAppointmentTheRecordBelongsTo() {
        UUID id = UUID.randomUUID();

        listener.onPrescriptionCreated(new PrescriptionCreatedEvent(id));

        verify(appointmentService).completeAfterExam(id);
    }

    @Test
    void aMalformedEventIsIgnoredInsteadOfBeingRedeliveredForever() {
        listener.onPrescriptionCreated(new PrescriptionCreatedEvent(null));
        listener.onPrescriptionCreated(null);

        verify(appointmentService, never()).completeAfterExam(any());
    }
}
