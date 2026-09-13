package com.vetclinic.booking.messaging;

import java.util.List;
import java.util.UUID;

/** Bản sao riêng của sự kiện user.locked / user.unlocked do auth-service phát (CN-08). */
public record UserStatusChangedEvent(UUID userId, List<String> roles, boolean locked) {
}
