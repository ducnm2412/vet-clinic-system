package com.vetclinic.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record GenerateSlotsRequest(@NotNull UUID doctorUserId, @NotNull LocalDate date) {
}
