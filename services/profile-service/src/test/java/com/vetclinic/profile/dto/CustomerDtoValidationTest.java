package com.vetclinic.profile.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerDtoValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void customerProfileRequest_futureDateOfBirth_violation() {
        var request = new CustomerProfileRequest("0901234567", LocalDate.now().plusDays(1));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void customerProfileRequest_pastDateOfBirth_noViolation() {
        var request = new CustomerProfileRequest("0901234567", LocalDate.of(1995, 5, 20));
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void addressRequest_blankRequiredFields_violation() {
        var request = new AddressRequest("", null, null, "", false);
        assertThat(VALIDATOR.validate(request)).hasSize(2);
    }

    @Test
    void addressRequest_valid_noViolation() {
        var request = new AddressRequest("123 Main St", null, null, "HCM", true);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

}
