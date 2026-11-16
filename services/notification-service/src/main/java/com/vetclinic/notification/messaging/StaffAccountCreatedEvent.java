package com.vetclinic.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Mirror của auth-service StaffAccountCreatedEvent: admin vừa tạo tài khoản DOCTOR/STAFF. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffAccountCreatedEvent(UUID userId, String role) {
}
