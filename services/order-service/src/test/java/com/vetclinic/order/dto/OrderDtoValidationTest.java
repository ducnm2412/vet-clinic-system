package com.vetclinic.order.dto;

import com.vetclinic.order.domain.PaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CheckoutRequest validCheckout() {
        return new CheckoutRequest("Nguyen Van A", "0901234567", "12 Le Loi, Q1, TP.HCM",
                "Giao giờ hành chính", PaymentMethod.COD);
    }

    @Test
    void validCheckout_hasNoViolation() {
        assertThat(validator.validate(validCheckout())).isEmpty();
    }

    @Test
    void phoneMustBeTenDigitsStartingWithZero() {
        assertThat(paths(new CheckoutRequest("A", "0123", "dc", null, PaymentMethod.COD)))
                .contains("recipientPhone");
        assertThat(paths(new CheckoutRequest("A", "1901234567", "dc", null, PaymentMethod.COD)))
                .contains("recipientPhone");
        assertThat(paths(new CheckoutRequest("A", "09012345678", "dc", null, PaymentMethod.COD)))
                .contains("recipientPhone");
    }

    @Test
    void blankAddress_isRejected() {
        assertThat(paths(new CheckoutRequest("A", "0901234567", "   ", null, PaymentMethod.COD)))
                .contains("shippingAddress");
    }

    @Test
    void missingPaymentMethod_isRejected() {
        assertThat(paths(new CheckoutRequest("A", "0901234567", "dc", null, null)))
                .contains("paymentMethod");
    }

    @Test
    void addToCart_quantityMustBeAtLeastOne() {
        assertThat(paths(new AddToCartRequest(UUID.randomUUID(), 0))).contains("quantity");
        assertThat(paths(new AddToCartRequest(UUID.randomUUID(), -3))).contains("quantity");
        assertThat(validator.validate(new AddToCartRequest(UUID.randomUUID(), 1))).isEmpty();
    }

    @Test
    void addToCart_productIdRequired() {
        assertThat(paths(new AddToCartRequest(null, 2))).contains("productId");
    }

    @Test
    void updateCartItem_zeroIsRejected() {
        // Bỏ hẳn sản phẩm thì dùng DELETE, không truyền 0.
        assertThat(paths(new UpdateCartItemRequest(0))).contains("quantity");
    }

    @Test
    void cancelReason_isRequired() {
        assertThat(paths(new CancelOrderRequest("  "))).contains("reason");
        assertThat(validator.validate(new CancelOrderRequest("Đổi ý"))).isEmpty();
    }

    private <T> Set<String> paths(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
