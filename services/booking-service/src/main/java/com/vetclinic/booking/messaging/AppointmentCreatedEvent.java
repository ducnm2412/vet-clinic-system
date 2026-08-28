package com.vetclinic.booking.messaging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentCreatedEvent(
        UUID appointmentId,
        UUID slotId,
        UUID doctorUserId,
        UUID customerUserId,
        UUID petId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
}
