package com.vetclinic.auth.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(String subject, List<String> roles) {
        return buildToken(subject, roles, jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(String subject) {
        return buildToken(subject, List.of(), jwtProperties.getRefreshTokenExpiration());
    }

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

    private String buildToken(String subject, List<String> roles, long expirationMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(subject)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
