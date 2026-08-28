package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record AppointmentStatusUpdateRequest(@NotNull AppointmentStatus status) {
}
