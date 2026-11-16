package com.vetclinic.profile.messaging;

import java.util.UUID;

/**
 * Bác sĩ hoặc nhân viên vừa lưu hồ sơ của mình. notification-service nghe để gỡ thông báo nhắc
 * "hãy cập nhật hồ sơ" — việc đã làm thì thông báo không còn nghĩa.
 */
public record ProfileUpdatedEvent(UUID userId, String role) {
}
