package com.vetclinic.chat.service;

import com.vetclinic.chat.config.ChatProperties;
import com.vetclinic.chat.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    /** Đồng hồ chỉnh tay được. */
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-24T03:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    private RateLimiter limiter(int guest, int user, int dailyCap) {
        ChatProperties properties = new ChatProperties(20, 1000, 4, 45,
                new ChatProperties.Gemini("k", "http://x", "m", List.of(), 15, 3, 25),
                new ChatProperties.RateLimit(10, guest, user, dailyCap),
                new ChatProperties.IntentSettings(ChatProperties.IntentMode.RULES, "", null));
        return new RateLimiter(properties, clock);
    }

    @Test
    void guestIsLimitedWithinWindow() {
        RateLimiter limiter = limiter(3, 10, 1000);
        Caller guest = Caller.guest("1.2.3.4");

        for (int i = 0; i < 3; i++) {
            limiter.acquire(guest);
        }
        assertThatThrownBy(() -> limiter.acquire(guest))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void windowSlidesSoOldHitsExpire() {
        RateLimiter limiter = limiter(2, 10, 1000);
        Caller guest = Caller.guest("1.2.3.4");
        limiter.acquire(guest);
        limiter.acquire(guest);

        clock.advanceSeconds(601);

        assertThatCode(() -> limiter.acquire(guest)).doesNotThrowAnyException();
    }

    @Test
    void loggedInUsersGetHigherLimitAndSeparateBuckets() {
        RateLimiter limiter = limiter(1, 3, 1000);
        Caller user = Caller.user("u1", "token");
        Caller guest = Caller.guest("1.2.3.4");

        for (int i = 0; i < 3; i++) {
            limiter.acquire(user);
        }
        assertThatThrownBy(() -> limiter.acquire(user)).isInstanceOf(RateLimitExceededException.class);
        // Một người hết hạn mức không ảnh hưởng người khác.
        assertThatCode(() -> limiter.acquire(guest)).doesNotThrowAnyException();
    }

    @Test
    void differentGuestsHaveSeparateBuckets() {
        RateLimiter limiter = limiter(1, 10, 1000);
        limiter.acquire(Caller.guest("1.1.1.1"));
        assertThatCode(() -> limiter.acquire(Caller.guest("2.2.2.2"))).doesNotThrowAnyException();
    }

    @Test
    void dailyCapBlocksEveryoneThenResetsNextDay() {
        RateLimiter limiter = limiter(100, 100, 2);
        limiter.acquire(Caller.guest("1.1.1.1"));
        limiter.acquire(Caller.guest("2.2.2.2"));

        assertThatThrownBy(() -> limiter.acquire(Caller.guest("3.3.3.3")))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("hôm nay");

        clock.advanceSeconds(24 * 3600);
        assertThatCode(() -> limiter.acquire(Caller.guest("3.3.3.3"))).doesNotThrowAnyException();
    }
}
