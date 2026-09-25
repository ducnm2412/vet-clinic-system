package com.vetclinic.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull UUID petId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        // VD-21: không bắt buộc — khách không chắc cần dịch vụ gì thì cứ mô tả ở ô lý do khám,
        // phòng khám đọc rồi xếp. Bắt chọn sẽ đẩy người ta chọn bừa.
        UUID serviceId,
        String reason
) {
}
