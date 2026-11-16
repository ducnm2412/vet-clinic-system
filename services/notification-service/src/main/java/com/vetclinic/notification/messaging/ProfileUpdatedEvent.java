package com.vetclinic.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Mirror của profile-service ProfileUpdatedEvent: bác sĩ/nhân viên vừa lưu hồ sơ của mình. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileUpdatedEvent(UUID userId, String role) {
}
