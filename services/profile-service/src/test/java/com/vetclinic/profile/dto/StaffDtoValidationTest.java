package com.vetclinic.profile.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StaffDtoValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void staffProfileRequest_blankPosition_violation() {
        var request = new StaffProfileRequest("", "0922222222", LocalDate.of(2023, 3, 1));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void staffProfileRequest_futureHireDate_violation() {
        var request = new StaffProfileRequest("Le tan", "0922222222", LocalDate.now().plusDays(1));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void staffProfileRequest_valid_noViolation() {
        var request = new StaffProfileRequest("Le tan", "0922222222", LocalDate.of(2023, 3, 1));
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
