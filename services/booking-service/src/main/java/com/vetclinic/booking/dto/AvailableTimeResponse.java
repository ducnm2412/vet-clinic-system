package com.vetclinic.booking.dto;

import java.time.LocalTime;

public record AvailableTimeResponse(LocalTime startTime, LocalTime endTime, long availableCount) {
}
