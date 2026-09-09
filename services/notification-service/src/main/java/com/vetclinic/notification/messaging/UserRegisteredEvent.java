package com.vetclinic.notification.messaging;

import java.time.Instant;
import java.util.UUID;

// Bản sao riêng của notification-service cho event user.registered (auth-service publish) — mỗi
// service tự sở hữu model của mình, Jackson chỉ cần khớp tên field JSON, không cần dùng chung
// class giữa hai service. Field phải khớp tên với auth-service's UserRegisteredEvent.
public record UserRegisteredEvent(UUID userId, String email, String firstName, String lastName,
                                   String verificationToken, Instant verificationTokenExpiresAt) {
}
