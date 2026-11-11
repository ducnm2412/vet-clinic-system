package com.vetclinic.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Admin đề xuất giờ khác cho một lịch không xếp được đúng giờ khách chọn. */
public record ProposeTimeRequest(
        @NotNull UUID doctorUserId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime
) {
}
