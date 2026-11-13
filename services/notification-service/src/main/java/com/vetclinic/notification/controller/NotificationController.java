package com.vetclinic.notification.controller;

import com.vetclinic.notification.dto.NotificationListResponse;
import com.vetclinic.notification.dto.NotificationResponse;
import com.vetclinic.notification.security.jwt.AuthenticatedUser;
import com.vetclinic.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Thông báo của chính người đang đăng nhập — userId luôn lấy từ token, không nhận từ client. */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public NotificationListResponse list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.list(principal.userId());
    }

    @PutMapping("/notifications/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @PathVariable UUID id) {
        return notificationService.markRead(principal.userId(), id);
    }

    @PutMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.markAllRead(principal.userId());
    }
}
