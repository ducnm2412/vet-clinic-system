package com.vetclinic.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        // Slug đi vào URL nên chỉ cho chữ thường, số và dấu gạch ngang.
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "slug chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
        @Size(max = 2000) String description
) {
}
