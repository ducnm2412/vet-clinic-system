package com.vetclinic.notification.service;

import com.vetclinic.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Xoá thông báo trong app quá cũ để bảng không phình mãi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    private final NotificationRepository repository;

    @Value("${app.notifications.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void purgeOld() {
        int deleted = repository.deleteOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)));
        if (deleted > 0) {
            log.info("Purged {} in-app notifications older than {} days", deleted, retentionDays);
        }
    }
}
