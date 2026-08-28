package com.vetclinic.booking.security;

import org.springframework.security.core.Authentication;

import java.util.Arrays;

public class RoleUtils {

    private RoleUtils() {
    }

    public static boolean hasAnyRole(Authentication authentication, String... roles) {
        return Arrays.stream(roles).anyMatch(role -> authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role)));
    }
}
