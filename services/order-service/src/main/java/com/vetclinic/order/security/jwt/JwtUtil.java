package com.vetclinic.order.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "userId";

    private final JwtProperties jwtProperties;

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public List<String> extractRoles(Claims claims) {
        List<?> raw = claims.get(ROLES_CLAIM, List.class);
        return raw == null ? List.of() : raw.stream().map(String::valueOf).toList();
    }

    public UUID extractUserId(Claims claims) {
        String raw = claims.get(USER_ID_CLAIM, String.class);
        if (raw == null) {
            throw new IllegalArgumentException("Token missing userId claim");
        }
        return UUID.fromString(raw);
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
