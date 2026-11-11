package com.vetclinic.pet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code items} trống hoặc null là hợp lệ: bệnh chỉ cần theo dõi thì mẫu không có thuốc. */
public record PrescriptionTemplateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank String diagnosis,
        String treatment,
        String notes,
        @Valid @Size(max = 30) List<PrescriptionTemplateItemRequest> items
) {
}
