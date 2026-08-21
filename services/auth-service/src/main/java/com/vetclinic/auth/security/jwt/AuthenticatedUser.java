package com.vetclinic.auth.security.jwt;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.UUID;

// getName() trả email (không phải userId) để Authentication#getName() ở code hiện có không bị phá vỡ.
public record AuthenticatedUser(UUID userId, String email) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }
}
