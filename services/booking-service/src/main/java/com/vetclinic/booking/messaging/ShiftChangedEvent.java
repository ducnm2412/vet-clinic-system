package com.vetclinic.booking.messaging;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Bản sao riêng của sự kiện shift.added / shift.removed do staff-service phát (CN-39, CN-41). */
public record ShiftChangedEvent(UUID userId, LocalDate date, LocalTime startTime, LocalTime endTime, boolean added) {
}
