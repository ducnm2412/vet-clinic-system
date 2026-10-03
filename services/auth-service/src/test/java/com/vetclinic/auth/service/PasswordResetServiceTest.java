package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.PasswordResetToken;
import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import com.vetclinic.auth.exception.InvalidRefreshTokenException;
import com.vetclinic.auth.exception.InvalidResetTokenException;
import com.vetclinic.auth.messaging.PasswordResetRequestedEvent;
import com.vetclinic.auth.repository.PasswordResetTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.TokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
@RecordApplicationEvents
class PasswordResetServiceTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEvents events;

    private void createActiveUser(String email) {
        authService.register(new RegisterRequest("Reset", "User", email, "oldpassword1", "oldpassword1"));
        authService.verifyEmail(userRepository.findByEmail(email).orElseThrow().getVerificationToken());
    }

    private List<PasswordResetRequestedEvent> resetEvents() {
        return events.stream(PasswordResetRequestedEvent.class).toList();
    }

    @Test
    void requestReset_unknownEmail_sameMessageAndNoEvent() {
        createActiveUser("known-reset@example.com");

        MessageResponse known = passwordResetService.requestReset("known-reset@example.com");
        MessageResponse unknown = passwordResetService.requestReset("nobody-reset@example.com");

        assertThat(unknown).isEqualTo(known);
        assertThat(resetEvents()).hasSize(1);
        assertThat(resetEvents().get(0).email()).isEqualTo("known-reset@example.com");
    }

    @Test
    void requestReset_inactiveUser_noEvent() {
        authService.register(new RegisterRequest("In", "Active", "inactive-reset@example.com", "oldpassword1", "oldpassword1"));

        passwordResetService.requestReset("inactive-reset@example.com");

        assertThat(resetEvents()).isEmpty();
    }

    @Test
    void requestReset_storesOnlyHashOfToken() {
        createActiveUser("hash-reset@example.com");

        passwordResetService.requestReset("hash-reset@example.com");

        String raw = resetEvents().get(0).resetToken();
        assertThat(passwordResetTokenRepository.findByTokenHash(raw)).isEmpty();
        assertThat(passwordResetTokenRepository.findByTokenHash(TokenHasher.hash(raw))).isPresent();
    }

    @Test
    void requestReset_twiceInCooldown_secondIsIgnored() {
        createActiveUser("cooldown-reset@example.com");

        passwordResetService.requestReset("cooldown-reset@example.com");
        passwordResetService.requestReset("cooldown-reset@example.com");

        assertThat(resetEvents()).hasSize(1);
    }

    @Test
    void reset_changesPassword_oldPasswordRejected() {
        createActiveUser("change-reset@example.com");
        passwordResetService.requestReset("change-reset@example.com");
        String raw = resetEvents().get(0).resetToken();

        passwordResetService.reset(raw, "newpassword1");

        AuthResponse login = authService.login(new LoginRequest("change-reset@example.com", "newpassword1"));
        assertThat(login.accessToken()).isNotBlank();
        assertThatThrownBy(() -> authService.login(new LoginRequest("change-reset@example.com", "oldpassword1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void reset_tokenCannotBeUsedTwice() {
        createActiveUser("twice-reset@example.com");
        passwordResetService.requestReset("twice-reset@example.com");
        String raw = resetEvents().get(0).resetToken();

        passwordResetService.reset(raw, "newpassword1");

        assertThatThrownBy(() -> passwordResetService.reset(raw, "another-password2"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void reset_expiredToken_throws() {
        createActiveUser("expired-reset@example.com");
        passwordResetService.requestReset("expired-reset@example.com");
        String raw = resetEvents().get(0).resetToken();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenHasher.hash(raw)).orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(1));
        passwordResetTokenRepository.saveAndFlush(token);

        assertThatThrownBy(() -> passwordResetService.reset(raw, "newpassword1"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void reset_unknownToken_throws() {
        assertThatThrownBy(() -> passwordResetService.reset("does-not-exist", "newpassword1"))
                .isInstanceOf(InvalidResetTokenException.class);
    }

    @Test
    void reset_revokesExistingRefreshTokens() {
        createActiveUser("revoke-reset@example.com");
        String refreshToken = authService.login(new LoginRequest("revoke-reset@example.com", "oldpassword1"))
                .refreshToken();
        passwordResetService.requestReset("revoke-reset@example.com");
        String raw = resetEvents().get(0).resetToken();

        passwordResetService.reset(raw, "newpassword1");

        assertThatThrownBy(() -> refreshTokenService.consume(refreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
