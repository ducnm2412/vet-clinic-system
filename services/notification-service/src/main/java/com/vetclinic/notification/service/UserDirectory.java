package com.vetclinic.notification.service;

import com.vetclinic.notification.client.AuthServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Danh sách người nhận theo vai trò ("mọi nhân viên", "admin"). Hỏi auth-service rồi nhớ ít phút:
 * một đợt bác sĩ lưu bệnh án liên tiếp không nên gọi auth-service mỗi lần. Nhân viên mới tạo trễ
 * vài phút mới nhận được thông báo là chấp nhận được.
 *
 * Auth-service lỗi thì dùng danh sách cũ nếu có (thông báo vẫn đi); chưa từng có danh sách thì ném
 * lỗi để RabbitMQ thử lại — không âm thầm làm rơi thông báo.
 */
@Slf4j
@Component
public class UserDirectory {

    private static final Duration TTL = Duration.ofMinutes(2);

    private record Cached(List<UUID> ids, Instant fetchedAt) {
    }

    private final AuthServiceClient authClient;
    private final String internalKey;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public UserDirectory(AuthServiceClient authClient, @Value("${app.internal-api-key:}") String internalKey) {
        this.authClient = authClient;
        this.internalKey = internalKey;
    }

    public List<UUID> idsByRole(String role) {
        Cached cached = cache.get(role);
        if (cached != null && cached.fetchedAt().plus(TTL).isAfter(Instant.now())) {
            return cached.ids();
        }
        try {
            List<UUID> ids = List.copyOf(authClient.userIdsByRole(role, internalKey));
            cache.put(role, new Cached(ids, Instant.now()));
            return ids;
        } catch (RuntimeException e) {
            if (cached != null) {
                log.warn("auth-service unreachable, using stale {} recipient list: {}", role, e.getMessage());
                return cached.ids();
            }
            throw e;
        }
    }
}
