package com.vetclinic.pet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Giới hạn độ dài khớp cột DB (và {@code prescription_items}) để lỗi trả 400 thay vì 500. */
public record PrescriptionTemplateItemRequest(
        @NotNull UUID medicationId,
        @NotBlank @Size(max = 100) String dosage,
        @NotBlank @Size(max = 100) String frequency,
        @Min(1) Integer durationDays,
        String notes
) {
}
