package com.vetclinic.product.dto;

import com.vetclinic.product.domain.StockMovementType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private ProductRequest validProduct() {
        return new ProductRequest(UUID.randomUUID(), "SKU-001", "Thức ăn cho chó", "Mô tả",
                new BigDecimal("120000.00"), "gói", null, 10, 5, true);
    }

    @Test
    void validProduct_hasNoViolation() {
        assertThat(validator.validate(validProduct())).isEmpty();
    }

    @Test
    void negativePrice_isRejected() {
        ProductRequest request = new ProductRequest(UUID.randomUUID(), "SKU-002", "Tên", null,
                new BigDecimal("-1"), "gói", null, 0, 0, true);

        assertThat(violationPaths(request)).contains("price");
    }

    @Test
    void blankSku_isRejected() {
        ProductRequest request = new ProductRequest(UUID.randomUUID(), "  ", "Tên", null,
                new BigDecimal("1000"), "gói", null, 0, 0, true);

        assertThat(violationPaths(request)).contains("sku");
    }

    @Test
    void missingCategoryId_isRejected() {
        ProductRequest request = new ProductRequest(null, "SKU-003", "Tên", null,
                new BigDecimal("1000"), "gói", null, 0, 0, true);

        assertThat(violationPaths(request)).contains("categoryId");
    }

    @Test
    void negativeInitialStock_isRejected() {
        ProductRequest request = new ProductRequest(UUID.randomUUID(), "SKU-004", "Tên", null,
                new BigDecimal("1000"), "gói", null, -5, 0, true);

        assertThat(violationPaths(request)).contains("initialStock");
    }

    @Test
    void categorySlug_mustBeKebabCase() {
        assertThat(violationPaths(new CategoryRequest("Thức ăn", "Thuc An", null))).contains("slug");
        assertThat(validator.validate(new CategoryRequest("Thức ăn", "thuc-an", null))).isEmpty();
    }

    @Test
    void stockAdjustment_zeroQuantity_isRejected() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(StockMovementType.IMPORT, 0, null);

        assertThat(violationPaths(request)).contains("quantityChangeNonZero");
    }

    @Test
    void stockAdjustment_negativeImport_isRejected() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(StockMovementType.IMPORT, -3, null);

        assertThat(violationPaths(request)).contains("signConsistentWithType");
    }

    @Test
    void stockAdjustment_negativeAdjustment_isAllowed() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(StockMovementType.ADJUSTMENT, -3, "Kiểm kê thiếu");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void stockAdjustment_saleType_isRejectedFromApi() {
        // SALE chỉ được sinh từ sự kiện RabbitMQ, không cho gọi thủ công qua API.
        StockAdjustmentRequest request = new StockAdjustmentRequest(StockMovementType.SALE, 5, null);

        assertThat(violationPaths(request)).contains("signConsistentWithType");
    }

    private <T> Set<String> violationPaths(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }
}
