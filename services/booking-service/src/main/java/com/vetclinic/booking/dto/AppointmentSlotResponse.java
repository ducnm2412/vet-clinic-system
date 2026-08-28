package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.SlotStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentSlotResponse(
        UUID id,
        UUID doctorUserId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        SlotStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
