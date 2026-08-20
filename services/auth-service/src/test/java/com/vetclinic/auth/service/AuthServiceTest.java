package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.CreateStaffAccountRequest;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.exception.EmailAlreadyExistsException;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import com.vetclinic.auth.exception.InvalidOrExpiredTokenException;
import com.vetclinic.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void register_createsInactiveUserAndBlocksLoginUntilVerified() {
        authService.register(new RegisterRequest("Test", "User", "test-user@example.com", "password123", "password123"));

        User user = userRepository.findByEmail("test-user@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(user.getVerificationToken()).isNotBlank();

        assertThatThrownBy(() -> authService.login(new LoginRequest("test-user@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);

        authService.verifyEmail(user.getVerificationToken());

        AuthResponse loginResponse = authService.login(new LoginRequest("test-user@example.com", "password123"));
        assertThat(loginResponse.accessToken()).isNotBlank();
    }

    @Test
    void verifyEmail_unknownToken_throws() {
        assertThatThrownBy(() -> authService.verifyEmail("does-not-exist"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void verifyEmail_expiredToken_throws() {
        authService.register(new RegisterRequest("Expired", "User", "expired@example.com", "password123", "password123"));

        User user = userRepository.findByEmail("expired@example.com").orElseThrow();
        user.setVerificationTokenExpiresAt(Instant.now().minusSeconds(60));
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> authService.verifyEmail(user.getVerificationToken()))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void createStaffAccount_createsActiveUserAndAllowsImmediateLogin() {
        authService.createStaffAccount(new CreateStaffAccountRequest(
                "House", "MD", "doctor@example.com", "password123", RoleName.DOCTOR));

        User user = userRepository.findByEmail("doctor@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getVerificationToken()).isNull();
        assertThat(user.getRoles()).extracting(role -> role.getName()).containsExactly(RoleName.DOCTOR);

        AuthResponse loginResponse = authService.login(new LoginRequest("doctor@example.com", "password123"));
        assertThat(loginResponse.accessToken()).isNotBlank();
    }

    @Test
    void createStaffAccount_duplicateEmail_throws() {
        authService.createStaffAccount(new CreateStaffAccountRequest(
                "Nurse", "Joy", "staffdup@example.com", "password123", RoleName.STAFF));

        assertThatThrownBy(() -> authService.createStaffAccount(new CreateStaffAccountRequest(
                "Nurse", "Joy2", "staffdup@example.com", "password123", RoleName.STAFF)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void register_duplicateEmail_throws() {
        authService.register(new RegisterRequest("Dup", "User", "dup@example.com", "password123", "password123"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Dup", "User", "dup@example.com", "password123", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void login_wrongPassword_throws() {
        authService.register(new RegisterRequest("Wrong", "Pass", "wrongpass@example.com", "password123", "password123"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("wrongpass@example.com", "wrongpassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsSameExceptionAsWrongPassword() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
