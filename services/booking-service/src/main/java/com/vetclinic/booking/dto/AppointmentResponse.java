package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.AppointmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID slotId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        UUID customerUserId,
        UUID petId,
        String reason,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
