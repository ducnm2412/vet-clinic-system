package com.vetclinic.profile.messaging;

import java.util.UUID;

/** Bản sao riêng của profile-service cho event user.customer-created (auth-service phát, CN-19). */
public record CustomerAccountCreatedEvent(UUID userId, String email, String fullName, String phone) {
}
