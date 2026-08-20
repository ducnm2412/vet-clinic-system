package com.vetclinic.auth.dto;

import com.vetclinic.auth.domain.RoleName;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateStaffAccountRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void doctorRole_noViolation() {
        CreateStaffAccountRequest request = new CreateStaffAccountRequest(
                "Test", "Doctor", "doctor@example.com", "password123", RoleName.DOCTOR);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void staffRole_noViolation() {
        CreateStaffAccountRequest request = new CreateStaffAccountRequest(
                "Test", "Staff", "staff@example.com", "password123", RoleName.STAFF);

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void customerRole_violation() {
        CreateStaffAccountRequest request = new CreateStaffAccountRequest(
                "Test", "User", "sneaky@example.com", "password123", RoleName.CUSTOMER);

        Set<ConstraintViolation<CreateStaffAccountRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("role must be DOCTOR or STAFF");
    }

    @Test
    void adminRole_violation() {
        CreateStaffAccountRequest request = new CreateStaffAccountRequest(
                "Test", "User", "sneaky2@example.com", "password123", RoleName.ADMIN);

        assertThat(VALIDATOR.validate(request)).hasSize(1);
    }
}
