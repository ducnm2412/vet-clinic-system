package com.vetclinic.auth.messaging;

import java.util.List;
import java.util.UUID;

/**
 * CN-08: tài khoản bị khoá ({@code locked = true}) hoặc mở khoá. Routing key {@code user.locked} /
 * {@code user.unlocked}. booking-service nghe để ngừng xếp lịch cho bác sĩ bị khoá.
 * Kèm {@code roles} để bên nghe khỏi phải gọi ngược hỏi người này là ai.
 */
public record UserStatusChangedEvent(UUID userId, List<String> roles, boolean locked) {
}
