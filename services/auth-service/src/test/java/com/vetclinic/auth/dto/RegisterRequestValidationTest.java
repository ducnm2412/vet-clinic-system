package com.vetclinic.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void matchingPasswords_noViolation() {
        RegisterRequest request = new RegisterRequest(
                "Test", "User", "test@example.com", "password123", "password123");

        Set<ConstraintViolation<RegisterRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void mismatchedPasswords_violation() {
        RegisterRequest request = new RegisterRequest(
                "Test", "User", "test@example.com", "password123", "different123");

        Set<ConstraintViolation<RegisterRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("password and confirmPassword must match");
    }
}
