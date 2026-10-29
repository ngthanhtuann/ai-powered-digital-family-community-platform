package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSearchRateLimiterTest {

    /** Đồng hồ chỉnh tay được để đi qua cửa sổ 1 giờ. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-30T00:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void blocksAfterLimitWithExactRetryAfterAndRecoversWhenWindowSlides() {
        MutableClock clock = new MutableClock();
        AiSearchRateLimiter limiter = new AiSearchRateLimiter(2, clock);
        UUID user = UUID.randomUUID();

        limiter.check(user);
        clock.advance(Duration.ofMinutes(10));
        limiter.check(user);
        clock.advance(Duration.ofMinutes(5));

        ApiException blocked = assertThrows(ApiException.class, () -> limiter.check(user));
        assertEquals(ErrorCode.RATE_001, blocked.code());
        // Lượt đầu ở phút 0 hết hạn ở phút 60; hiện là phút 15 nên còn 45 phút.
        assertEquals(45L * 60 + 1, blocked.retryAfterSeconds().longValue());
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID()), "người khác không bị ảnh hưởng");

        clock.advance(Duration.ofMinutes(46));
        assertDoesNotThrow(() -> limiter.check(user));
    }
}
