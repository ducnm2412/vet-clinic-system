package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.PasswordResetToken;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.exception.InvalidResetTokenException;
import com.vetclinic.auth.messaging.PasswordResetRequestedEvent;
import com.vetclinic.auth.repository.PasswordResetTokenRepository;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Quên mật khẩu. Hai bước: yêu cầu (gửi link qua email) rồi đặt lại (dùng link đó).
 *
 * Token là 32 byte ngẫu nhiên, DB chỉ giữ bản băm SHA-256 — cùng cách với refresh token. Mỗi
 * lúc chỉ một link có hiệu lực: yêu cầu mới huỷ link cũ.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    // Chặn spam: một người không bắn được hàng loạt email vào hộp thư của người khác.
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

    static final String REQUEST_ACCEPTED_MESSAGE =
            "If that email is registered, we have sent password reset instructions to it.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Luôn trả cùng một thông báo dù email có tồn tại hay không — trả khác đi là cho kẻ dò biết
     * email nào có tài khoản. Chỉ tài khoản ACTIVE mới được gửi: INACTIVE phải xác minh email
     * trước, LOCKED thì không được tự mở lại bằng đường này. Tài khoản đăng ký qua Google/Facebook
     * (chưa có mật khẩu) vẫn dùng được để đặt mật khẩu lần đầu.
     */
    @Transactional
    public MessageResponse requestReset(String email) {
        userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(this::issueToken);

        return new MessageResponse(REQUEST_ACCEPTED_MESSAGE);
    }

    private void issueToken(User user) {
        Instant now = Instant.now();

        if (passwordResetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(REQUEST_COOLDOWN))) {
            return;
        }

        passwordResetTokenRepository.invalidateAllActiveForUser(user.getId(), now);

        String raw = TokenHasher.newRawToken();
        Instant expiresAt = now.plus(TOKEN_TTL);
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(TokenHasher.hash(raw))
                .expiresAt(expiresAt)
                .build());

        // Chỉ đẩy lên RabbitMQ sau khi commit (UserEventPublisher, AFTER_COMMIT) — rollback thì
        // không có email nào chứa link trỏ tới một token không tồn tại.
        applicationEventPublisher.publishEvent(new PasswordResetRequestedEvent(
                user.getId(), user.getEmail(), user.getFirstName(), raw, expiresAt));
    }

    /**
     * Đổi mật khẩu bằng link. Xong thì thu hồi MỌI refresh token của người đó: người đi đặt lại
     * mật khẩu thường là vì nghi bị lộ tài khoản, nên mọi thiết bị đang đăng nhập phải bị đá ra.
     * (Access token đã cấp vẫn sống nốt tối đa 15 phút — không có cách thu hồi JWT stateless.)
     */
    @Transactional
    public MessageResponse reset(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(TokenHasher.hash(rawToken))
                .orElseThrow(InvalidResetTokenException::new);

        Instant now = Instant.now();
        if (token.isUsed() || token.isExpired(now)) {
            throw new InvalidResetTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(InvalidResetTokenException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);

        return new MessageResponse("Password has been reset. You can now log in with your new password.");
    }
}
