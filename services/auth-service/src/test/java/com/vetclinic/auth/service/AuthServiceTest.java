package com.vetclinic.auth.service;

import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.exception.EmailAlreadyExistsException;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    void registerThenLogin_returnsValidTokens() {
        AuthResponse registerResponse = authService.register(
                new RegisterRequest("test-user@example.com", "password123"));

        assertThat(registerResponse.accessToken()).isNotBlank();
        assertThat(registerResponse.refreshToken()).isNotBlank();

        AuthResponse loginResponse = authService.login(
                new LoginRequest("test-user@example.com", "password123"));

        assertThat(loginResponse.accessToken()).isNotBlank();
    }

    @Test
    void register_duplicateEmail_throws() {
        authService.register(new RegisterRequest("dup@example.com", "password123"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("dup@example.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void login_wrongPassword_throws() {
        authService.register(new RegisterRequest("wrongpass@example.com", "password123"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("wrongpass@example.com", "wrongpassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsSameExceptionAsWrongPassword() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
