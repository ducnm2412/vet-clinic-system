package com.vetclinic.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull UUID petId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        String reason
) {
}
