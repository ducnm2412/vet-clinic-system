package com.vetclinic.booking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** VD-12: chốt chặn phải từ chối database thật và cho qua database test. */
class TestDatabaseGuardTest {

    @Test
    void refusesRealDatabase() {
        assertThatThrownBy(() -> TestDatabaseGuard.checkUrl("jdbc:postgresql://localhost:5437/booking_db"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("booking_db");
    }

    @Test
    void allowsTestDatabaseAndNoDatabaseAtAll() {
        assertThatCode(() -> TestDatabaseGuard.checkUrl("jdbc:postgresql://localhost:5437/booking_db_test?sslmode=disable"))
                .doesNotThrowAnyException();
        assertThatCode(() -> TestDatabaseGuard.checkUrl(null)).doesNotThrowAnyException();
    }

    @Test
    void refusesNameThatMerelyContainsTest() {
        // "test_booking_db" hay "booking_db_testing" deu KHONG phai database test cua du an.
        assertThatThrownBy(() -> TestDatabaseGuard.checkUrl("jdbc:postgresql://localhost:5437/booking_db_testing"))
                .isInstanceOf(IllegalStateException.class);
    }
}
