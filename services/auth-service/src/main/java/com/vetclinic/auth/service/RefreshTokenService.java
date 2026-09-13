package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.RefreshToken;
import com.vetclinic.auth.exception.InvalidRefreshTokenException;
import com.vetclinic.auth.repository.RefreshTokenRepository;
import com.vetclinic.auth.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * VD-05 — vòng đời refresh token.
 *
 * Trước đây refresh token là một JWT ký cùng khoá với access token, không lưu ở đâu cả, và
 * cũng không có endpoint nào nhận nó. Không lưu thì không thu hồi được: lộ ra là kẻ gian dùng
 * được trọn 7 ngày, bấm đăng xuất cũng vô ích.
 *
 * Bây giờ:
 * - Token là 32 byte ngẫu nhiên, không phải JWT — không có cách nào đem nó thay access token.
 * - Server chỉ lưu bản băm SHA-256.
 * - Mỗi lần làm mới là xoay vòng: token cũ bị thu hồi, sinh token mới.
 * - Token đã xoay vòng mà vẫn bị đem ra dùng thì thu hồi sạch mọi phiên của tài khoản đó.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Sinh một refresh token mới cho người dùng. Trả về chuỗi gốc — lần duy nhất nó tồn tại. */
    @Transactional
    public String issue(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash(raw))
                .expiresAt(Instant.now().plus(Duration.ofMillis(jwtProperties.getRefreshTokenExpiration())))
                .build());

        return raw;
    }

    /**
     * Dùng một refresh token: kiểm tra rồi thu hồi nó ngay, trả về chủ của nó.
     *
     * `noRollbackFor` không phải trang trí. Khi phát hiện token bị dùng lại, việc thu hồi mọi
     * phiên xảy ra NGAY TRƯỚC khi ném lỗi — mà @Transactional mặc định rollback khi có
     * exception, tức là thu hồi xong lại bị huỷ ngầm và kẻ gian vẫn giữ được phiên.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public UUID consume(String raw) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(raw))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if (token.isRevoked()) {
            // Token đã xoay vòng mà vẫn có người cầm — có hai bên cùng giữ nó, và không biết
            // bên nào là chủ thật. Cắt hết, buộc đăng nhập lại bằng mật khẩu.
            refreshTokenRepository.revokeAllActiveForUser(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }

        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        token.setRevokedAt(now);
        return token.getUserId();
    }

    /** Đăng xuất. Token không tồn tại hay đã thu hồi thì thôi, không báo lỗi. */
    @Transactional
    public void revoke(String raw) {
        refreshTokenRepository.findByTokenHash(hash(raw))
                .filter(t -> !t.isRevoked())
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Mọi JVM đều bắt buộc có SHA-256; tới được đây là môi trường hỏng, không phải lỗi dữ liệu.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
