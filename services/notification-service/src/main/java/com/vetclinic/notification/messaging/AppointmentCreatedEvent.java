package com.vetclinic.notification.messaging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Mirror của booking-service AppointmentCreatedEvent (CN-43). */
public record AppointmentCreatedEvent(
        UUID appointmentId,
        UUID slotId,
        UUID doctorUserId,
        UUID customerUserId,
        UUID petId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String customerEmail,
        String petName,
        String doctorName,
        String reason
) {
}
