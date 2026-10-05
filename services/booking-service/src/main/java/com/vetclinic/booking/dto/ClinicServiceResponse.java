package com.vetclinic.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ClinicServiceResponse(
        UUID id,
        String slug,
        String name,
        String description,
        Integer durationMinutes,
        BigDecimal referencePrice,
        boolean active,
        Integer displayOrder
) {
}
