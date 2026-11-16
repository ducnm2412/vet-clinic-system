package com.vetclinic.notification.service;

import com.vetclinic.notification.domain.Notification;
import com.vetclinic.notification.dto.NotificationListResponse;
import com.vetclinic.notification.dto.NotificationResponse;
import com.vetclinic.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Chuông chỉ cần vài chục thông báo gần nhất; không phân trang để giữ giao diện đơn giản. */
    public static final int MAX_LIST = 30;

    private final NotificationRepository repository;

    @Transactional
    public Notification create(UUID userId, String type, String title, String message, String link) {
        return repository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(truncate(title, 200))
                .message(truncate(message, 1000))
                .link(truncate(link, 300))
                .build());
    }

    /**
     * Tạo thông báo loại này cho người này nếu họ chưa có — cho thông báo chỉ gửi một lần trong đời
     * (nhắc cập nhật hồ sơ), an toàn khi RabbitMQ giao lại cùng một sự kiện.
     *
     * @return {@code true} nếu vừa tạo mới
     */
    @Transactional
    public boolean createOnce(UUID userId, String type, String title, String message, String link) {
        if (repository.existsByUserIdAndType(userId, type)) {
            return false;
        }
        create(userId, type, title, message, link);
        return true;
    }

    /** Gỡ mọi thông báo loại này của người này (việc được nhắc đã làm xong). @return số thông báo đã gỡ */
    @Transactional
    public int removeByType(UUID userId, String type) {
        return repository.deleteByUserIdAndType(userId, type);
    }

    /** Cùng một thông báo cho nhiều người, trong một transaction: lỗi giữa chừng thì không ai có bản nào, thử lại không nhân đôi. */
    @Transactional
    public void createForAll(Collection<UUID> userIds, String type, String title, String message, String link) {
        userIds.forEach(userId -> create(userId, type, title, message, link));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID userId) {
        var items = repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_LIST)).stream()
                .map(NotificationResponse::from)
                .toList();
        return new NotificationListResponse(items, repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo."));
        if (!n.isRead()) {
            n.markRead(Instant.now());
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllRead(userId, Instant.now());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
