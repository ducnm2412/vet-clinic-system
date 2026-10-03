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
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.vetclinic.auth.exception.AccountLockedException;
import com.vetclinic.auth.exception.EmailAlreadyExistsException;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import com.vetclinic.auth.exception.InvalidGoogleTokenException;
import com.vetclinic.auth.exception.InvalidOrExpiredTokenException;
import com.vetclinic.auth.exception.InvalidRefreshTokenException;
import com.vetclinic.auth.exception.UserNotFoundException;
import com.vetclinic.auth.messaging.UserDeletedEvent;
import com.vetclinic.auth.messaging.StaffAccountCreatedEvent;
import com.vetclinic.auth.messaging.UserRegisteredEvent;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.facebook.FacebookProfile;
import com.vetclinic.auth.security.facebook.FacebookTokenVerifier;
import com.vetclinic.auth.security.google.GoogleTokenVerifier;
import com.vetclinic.auth.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final FacebookTokenVerifier facebookTokenVerifier;
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

        // Publish event nội bộ (Spring, không phải RabbitMQ) — UserEventPublisher sẽ chỉ đẩy lên
        // RabbitMQ thật SAU KHI transaction này commit thành công (@TransactionalEventListener
        // AFTER_COMMIT), tránh trường hợp DB rollback nhưng email "chào mừng" vẫn lỡ được gửi.
        applicationEventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(), verificationToken, user.getVerificationTokenExpiresAt()));

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

        // VD-20: mang họ tên sang profile-service. Chỉ đẩy lên RabbitMQ sau khi commit.
        applicationEventPublisher.publishEvent(new StaffAccountCreatedEvent(user.getId(), user.getEmail(),
                (user.getFirstName() + " " + user.getLastName()).trim(), request.role().name()));

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

    // Không còn readOnly: đăng nhập giờ ghi một dòng refresh_tokens.
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // passwordHash null nghia la tai khoan tao qua Google, chua tung dat mat khau - tra
        // cung mot loi chung, khong noi rieng ra keo lo tai khoan nao dang nhap kieu gi.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    /**
     * Dang nhap/dang ky bang Google. idToken da duoc trinh duyet lay thang tu Google (luong
     * ID token, khong phai Authorization Code) nen khong can client secret hay redirect qua
     * gateway - chi can xac minh chu ky ngay tren server.
     *
     * Tim theo googleSub truoc, roi moi toi email: mot tai khoan da dang ky bang mat khau ma
     * dang nhap Google voi cung email thi duoc gan luon googleSub vao (Google da xac minh chu
     * so huu email do), tu do dang nhap duoc bang ca hai cach. Khong tim thay ca hai moi tao
     * moi - luon ACTIVE va khong dat mat khau, vi Google da xac minh email thay minh roi.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(idToken);

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidGoogleTokenException();
        }

        String googleSub = payload.getSubject();

        User user = userRepository.findByGoogleSub(googleSub)
                .or(() -> userRepository.findByEmail(payload.getEmail()))
                .orElseGet(() -> createGoogleUser(payload));

        if (user.getGoogleSub() == null) {
            user.setGoogleSub(googleSub);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }
        // Email da xac minh boi Google roi - tai khoan dang INACTIVE (vi du tu dang ky bang
        // mat khau nhung chua bam link) thi kich hoat luon, khong bat cho xac minh lai.
        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
        }

        return buildAuthResponse(user);
    }

    private User createGoogleUser(GoogleIdToken.Payload payload) {
        Role defaultRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Default role CUSTOMER not seeded"));

        User user = User.builder()
                .firstName(googleFirstName(payload))
                .lastName(googleLastName(payload))
                .email(payload.getEmail())
                .googleSub(payload.getSubject())
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();

        return userRepository.save(user);
    }

    private String googleFirstName(GoogleIdToken.Payload payload) {
        String givenName = (String) payload.get("given_name");
        if (givenName != null && !givenName.isBlank()) return givenName;

        String fullName = (String) payload.get("name");
        return fullName != null && !fullName.isBlank() ? fullName : payload.getEmail();
    }

    private String googleLastName(GoogleIdToken.Payload payload) {
        String familyName = (String) payload.get("family_name");
        return familyName != null ? familyName : "";
    }

    /**
     * Dang nhap/dang ky bang Facebook. Cung logic tim/tao/gan lien ket voi loginWithGoogle,
     * chi khac o buoc xac minh: FacebookTokenVerifier da goi Graph API kiem accessToken va lay
     * ho so ve, AuthService chi con lo phan tim/tao user.
     */
    @Transactional
    public AuthResponse loginWithFacebook(String accessToken) {
        FacebookProfile profile = facebookTokenVerifier.verify(accessToken);

        User user = userRepository.findByFacebookId(profile.id())
                .or(() -> userRepository.findByEmail(profile.email()))
                .orElseGet(() -> createFacebookUser(profile));

        if (user.getFacebookId() == null) {
            user.setFacebookId(profile.id());
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
        }

        return buildAuthResponse(user);
    }

    private User createFacebookUser(FacebookProfile profile) {
        Role defaultRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Default role CUSTOMER not seeded"));

        User user = User.builder()
                .firstName(profile.firstName() != null && !profile.firstName().isBlank()
                        ? profile.firstName() : profile.email())
                .lastName(profile.lastName() != null ? profile.lastName() : "")
                .email(profile.email())
                .facebookId(profile.id())
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();

        return userRepository.save(user);
    }

    /**
     * VD-05 — đổi refresh token lấy cặp token mới.
     *
     * Kiểm lại tài khoản mỗi lần làm mới, không chỉ kiểm token: tài khoản đã bị xoá hoặc
     * không còn ACTIVE thì token còn hạn cũng không được dùng nữa.
     *
     * `noRollbackFor` phải có ở cả tầng này. RefreshTokenService.consume ghi lệnh thu hồi rồi
     * mới ném lỗi; nếu method bao ngoài vẫn rollback theo mặc định thì lệnh thu hồi đó mất.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String refreshToken) {
        UUID userId = refreshTokenService.consume(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidRefreshTokenException();
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
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
        String refreshToken = refreshTokenService.issue(user.getId());

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
}
