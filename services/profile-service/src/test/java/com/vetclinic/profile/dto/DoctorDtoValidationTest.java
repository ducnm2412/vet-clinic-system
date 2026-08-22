package com.vetclinic.profile.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorDtoValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void doctorProfileRequest_blankSpecialty_violation() {
        var request = new DoctorProfileRequest("", "0911111111", "bio", 5);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void doctorProfileRequest_negativeYearsOfExperience_violation() {
        var request = new DoctorProfileRequest("Ngoai khoa", "0911111111", "bio", -1);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void doctorProfileRequest_valid_noViolation() {
        var request = new DoctorProfileRequest("Ngoai khoa", "0911111111", "bio", 5);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void doctorLicenseRequest_missingIssuedDate_violation() {
        var request = new DoctorLicenseRequest("VN-1", "Bo Y Te", null, null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void doctorLicenseRequest_futureIssuedDate_violation() {
        var request = new DoctorLicenseRequest("VN-1", "Bo Y Te", LocalDate.now().plusDays(1), null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void doctorLicenseRequest_valid_noViolation() {
        var request = new DoctorLicenseRequest("VN-1", "Bo Y Te", LocalDate.of(2015, 1, 1), null);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
