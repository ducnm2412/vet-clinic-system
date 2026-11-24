package com.vetclinic.chat.service;

import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Giới hạn số lượt gọi Gemini: cửa sổ trượt theo từng người (vãng lai theo IP, đã đăng nhập theo
 * userId) cộng trần tổng số lượt mỗi ngày. Bộ đếm nằm trong bộ nhớ nên chỉ đúng với một instance;
 * cần chạy nhiều instance thì chuyển sang Redis (đã có trong docker-compose).
 *
 * Câu trả lời của tầng rule (khẩn cấp, chào hỏi...) không đi qua đây nên không tốn hạn mức.
 */
@Component
public class RateLimiter {

    private static final int PRUNE_THRESHOLD = 5_000;

    private final ChatProperties.RateLimit config;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();
    private LocalDate dailyDate;
    private int dailyCount;

    public RateLimiter(ChatProperties properties, Clock clock) {
        this.config = properties.rateLimit();
        this.clock = clock;
    }

    /** Ghi nhận một lượt, hoặc ném RateLimitExceededException nếu người này (hay cả hệ thống hôm nay) đã hết hạn mức. */
    public synchronized void acquire(Caller caller) {
        long now = clock.millis();
        long windowMillis = Duration.ofMinutes(config.windowMinutes()).toMillis();

        LocalDate today = LocalDate.now(clock);
        if (!today.equals(dailyDate)) {
            dailyDate = today;
            dailyCount = 0;
        }
        if (dailyCount >= config.dailyCap()) {
            ZonedDateTime tomorrow = today.plusDays(1).atStartOfDay(clock.getZone());
            long retry = Math.max(1, Duration.between(ZonedDateTime.now(clock), tomorrow).toSeconds());
            throw new RateLimitExceededException(
                    "Chatbot đã hết lượt trả lời trong hôm nay. Bạn vui lòng quay lại vào ngày mai hoặc gọi hotline của phòng khám.",
                    retry);
        }

        Deque<Long> recent = hits.computeIfAbsent(caller.rateKey(), key -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() >= windowMillis) {
            recent.pollFirst();
        }
        int limit = caller.loggedIn() ? config.userPerWindow() : config.guestPerWindow();
        if (recent.size() >= limit) {
            long retry = Math.max(1, (recent.peekFirst() + windowMillis - now + 999) / 1000);
            throw new RateLimitExceededException(
                    "Bạn nhắn hơi nhiều trong thời gian ngắn. Vui lòng thử lại sau " + friendly(retry) + ".", retry);
        }

        recent.addLast(now);
        dailyCount++;
        if (hits.size() > PRUNE_THRESHOLD) {
            prune(now, windowMillis);
        }
    }

    private void prune(long now, long windowMillis) {
        hits.values().removeIf(deque -> deque.isEmpty() || now - deque.peekLast() >= windowMillis);
    }

    private static String friendly(long seconds) {
        if (seconds < 60) {
            return seconds + " giây";
        }
        return ((seconds + 59) / 60) + " phút";
    }
}
