package com.vetclinic.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignDoctorRequest(@NotNull UUID doctorUserId) {
}
