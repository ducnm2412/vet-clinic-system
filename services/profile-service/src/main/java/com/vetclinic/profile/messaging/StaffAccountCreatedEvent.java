package com.vetclinic.profile.messaging;

import java.util.UUID;

/** Mirror của auth-service StaffAccountCreatedEvent (VD-20). */
public record StaffAccountCreatedEvent(UUID userId, String email, String fullName, String role) {
}
