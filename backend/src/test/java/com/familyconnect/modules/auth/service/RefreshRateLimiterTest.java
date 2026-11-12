package com.familyconnect.modules.auth.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T00:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void limitsByIpReturnsRate001AndRecoversNextMinute() {
        MutableClock clock = new MutableClock();
        RefreshRateLimiter limiter = new RefreshRateLimiter(2, clock);

        limiter.check("198.51.100.10");
        clock.advance(Duration.ofSeconds(10));
        limiter.check("198.51.100.10");
        ApiException blocked = assertThrows(ApiException.class, () -> limiter.check("198.51.100.10"));
        assertEquals(ErrorCode.RATE_001, blocked.code());
        assertEquals(50L, blocked.retryAfterSeconds().longValue());

        assertDoesNotThrow(() -> limiter.check("198.51.100.11"));
        clock.advance(Duration.ofSeconds(50));
        assertDoesNotThrow(() -> limiter.check("198.51.100.10"));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshRateLimiter(0, Clock.systemUTC()));
    }
}
