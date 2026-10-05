package com.vetclinic.pet.messaging;

import java.util.UUID;

/** Bản sao riêng của sự kiện user.deleted do auth-service phát. */
public record UserDeletedEvent(UUID userId) {
}
