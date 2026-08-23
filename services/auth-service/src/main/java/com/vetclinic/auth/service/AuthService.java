package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.Role;
import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.CreateStaffAccountRequest;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.dto.UserResponse;
import com.vetclinic.auth.exception.EmailAlreadyExistsException;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import com.vetclinic.auth.exception.InvalidOrExpiredTokenException;
import com.vetclinic.auth.exception.UserNotFoundException;
import com.vetclinic.auth.messaging.UserDeletedEvent;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Role defaultRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Default role CUSTOMER not seeded"));

        String verificationToken = generateVerificationToken();

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.INACTIVE)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .verificationToken(verificationToken)
                .verificationTokenExpiresAt(Instant.now().plus(VERIFICATION_TOKEN_TTL))
                .build();

        userRepository.save(user);

        sendVerificationEmail(user, verificationToken);

        return new MessageResponse("Registration successful. Please check your email to verify your account.");
    }

    @Transactional
    public MessageResponse createStaffAccount(CreateStaffAccountRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Role role = roleRepository.findByName(request.role())
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + request.role()));

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(role)))
                .build();

        userRepository.save(user);

        return new MessageResponse("Account created for " + request.email() + " with role " + request.role());
    }

    @Transactional
    public MessageResponse deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        userRepository.delete(user);
        // Publish event nội bộ (Spring, không phải RabbitMQ) — UserEventPublisher sẽ chỉ đẩy lên
        // RabbitMQ thật SAU KHI transaction này commit thành công (@TransactionalEventListener
        // AFTER_COMMIT), tránh trường hợp DB rollback nhưng message "đã xoá" vẫn lỡ bay đi.
        applicationEventPublisher.publishEvent(new UserDeletedEvent(userId));

        return new MessageResponse("User deleted: " + userId);
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(InvalidOrExpiredTokenException::new);

        if (user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOrExpiredTokenException();
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);

        return new MessageResponse("Email verified successfully. You can now log in.");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getStatus(), extractRoleNames(user), user.getCreatedAt());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), extractRoleNames(user));
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        return new AuthResponse(accessToken, refreshToken);
    }

    private List<String> extractRoleNames(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .toList();
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // TODO: thay bằng publish message lên RabbitMQ để notification-service gửi email thật.
    private void sendVerificationEmail(User user, String token) {
        log.info("[DEV] Verification link for {}: GET /auth/verify-email?token={}", user.getEmail(), token);
    }
}
