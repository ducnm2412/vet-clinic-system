package com.vetclinic.notification.messaging;

import java.time.Instant;
import java.util.UUID;

// Bản sao riêng của notification-service cho event user.password-reset-requested (auth-service
// publish). Field phải khớp tên với auth-service's PasswordResetRequestedEvent.
public record PasswordResetRequestedEvent(UUID userId, String email, String firstName, String resetToken,
                                          Instant resetTokenExpiresAt) {
}
