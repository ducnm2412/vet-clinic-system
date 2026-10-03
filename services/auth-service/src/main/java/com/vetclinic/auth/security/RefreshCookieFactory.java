package com.vetclinic.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie mang refresh token: httpOnly (JS không đọc được, chống bị lấy cắp qua XSS),
 * SameSite=Lax (trình duyệt không gửi kèm trên request cross-site, chống CSRF cho /refresh và
 * /logout mà không cần cơ chế CSRF token riêng), Secure bật ở production (chỉ gửi qua HTTPS) —
 * đọc từ REFRESH_COOKIE_SECURE, mặc định false để chạy được ở dev (http://localhost:3000).
 *
 * Path cố định "/api/auth": đây là path trình duyệt NHÌN THẤY (qua rewrite proxy của Next.js ở
 * frontend), không phải path nội bộ "/auth" của service này — cookie phải khớp đúng path phía
 * trình duyệt mới được tự động gửi kèm khi gọi /api/auth/refresh, /api/auth/logout.
 */
@Component
public class RefreshCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookieFactory(
            @Value("${app.refresh-cookie.secure}") boolean secure,
            @Value("${jwt.refresh-token-expiration}") long maxAgeMs) {
        this.secure = secure;
        this.maxAge = Duration.ofMillis(maxAgeMs);
    }

    public ResponseCookie build(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH);
    }
}
