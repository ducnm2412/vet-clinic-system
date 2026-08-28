package com.vetclinic.booking.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// booking-service không tự phát token (chỉ verify) nên test tự dựng JWT hợp lệ bằng chung
// JWT_SECRET, mô phỏng đúng token mà auth-service sẽ phát hành thật.
public final class TestJwtSupport {

    private TestJwtSupport() {
    }

    public static String token(String secret, UUID userId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 600_000);

        return Jwts.builder()
                .subject("tester@example.com")
                .claim("userId", userId.toString())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
