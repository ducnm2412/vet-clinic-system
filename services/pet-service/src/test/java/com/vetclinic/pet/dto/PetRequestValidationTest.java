package com.vetclinic.pet.dto;

import com.vetclinic.pet.domain.PetGender;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Ràng buộc trên dữ liệu khách gửi lên, kiểm trước khi chạm database. */
class PetRequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankNameOrSpecies_violation() {
        assertThat(VALIDATOR.validate(new PetRequest(" ", "Chó", null, null, null, null, null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new PetRequest("Milo", "", null, null, null, null, null, null))).isNotEmpty();
    }

    @Test
    void negativeWeight_violation() {
        assertThat(VALIDATOR.validate(new PetRequest("Milo", "Chó", null, null, null, BigDecimal.valueOf(-1), null, null)))
                .isNotEmpty();
    }

    @Test
    void futureDateOfBirth_violation() {
        assertThat(VALIDATOR.validate(new PetRequest("Milo", "Chó", null, null, LocalDate.now().plusDays(1), null, null, null)))
                .isNotEmpty();
    }

    @Test
    void valid_noViolation() {
        assertThat(VALIDATOR.validate(new PetRequest("Milo", "Chó", "Poodle", PetGender.MALE,
                LocalDate.now(), BigDecimal.TEN, null, null))).isEmpty();
    }
}
