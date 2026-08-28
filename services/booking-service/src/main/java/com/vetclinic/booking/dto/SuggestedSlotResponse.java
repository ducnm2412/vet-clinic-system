package com.vetclinic.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record SuggestedSlotResponse(LocalDate date, LocalTime startTime, LocalTime endTime) {
}
