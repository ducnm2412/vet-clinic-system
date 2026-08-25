package com.vetclinic.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String unit,
        String imageUrl,
        Integer stockQuantity,
        Integer lowStockThreshold,
        Boolean lowStock,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
