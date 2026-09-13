package com.vetclinic.booking.dto;

import com.vetclinic.booking.domain.AppointmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentDetailResponse(
        UUID id,
        UUID slotId,
        // VD-16: bác sĩ phụ trách. Lấy từ slot — khách không chọn bác sĩ, hệ thống tự xếp, nên
        // response là chỗ duy nhất người ta biết được mình được xếp cho ai.
        UUID doctorUserId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        UUID customerUserId,
        UUID petId,
        String reason,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt,
        PetResponse pet
) {
}
