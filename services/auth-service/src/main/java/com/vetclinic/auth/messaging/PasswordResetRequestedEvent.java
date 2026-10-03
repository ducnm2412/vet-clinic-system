package com.vetclinic.auth.messaging;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetRequestedEvent(UUID userId, String email, String firstName,
                                          String resetToken, Instant resetTokenExpiresAt) {
}
