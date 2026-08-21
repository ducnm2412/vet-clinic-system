package com.vetclinic.profile.security.jwt;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.UUID;

// getName() trả email (không phải userId) để Authentication#getName() dùng nhất quán như auth-service.
public record AuthenticatedUser(UUID userId, String email) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }
}
