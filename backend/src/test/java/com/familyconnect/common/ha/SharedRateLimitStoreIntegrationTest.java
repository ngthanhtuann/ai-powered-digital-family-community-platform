package com.familyconnect.common.ha;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-96: bộ đếm RATE_001 dùng chung trên PostgreSQL thật (bảng V22).
 * Hai đối tượng store mô phỏng hai backend: chung DB nên chung hạn mức. Đồng hồ giả để thử hết cửa sổ.
 */
@SpringBootTest
class SharedRateLimitStoreIntegrationTest {
    private static final String SCOPE = "TEST_HA";

    @Autowired private JdbcTemplate jdbc;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-14T08:00:00Z"));
    private final String subject = UUID.randomUUID().toString();

    @AfterEach void cleanUp() {
        jdbc.update("DELETE FROM ha_rate_limit_fixed WHERE scope = ?", SCOPE);
        jdbc.update("DELETE FROM ha_rate_limit_sliding WHERE scope = ?", SCOPE);
    }

    @Test void fixedWindowIsSharedBetweenBackendsAndResetsAfterWindow() {
        SharedRateLimitStore primary = new SharedRateLimitStore(jdbc, clock);
        SharedRateLimitStore secondary = new SharedRateLimitStore(jdbc, clock);
        for (int i = 0; i < 3; i++) (i % 2 == 0 ? primary : secondary).checkFixed(SCOPE, subject, 3, 60);

        clock.advance(Duration.ofSeconds(20));
        assertThatThrownBy(() -> secondary.checkFixed(SCOPE, subject, 3, 60))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.RATE_001));

        clock.advance(Duration.ofSeconds(41)); // quá 60 giây kể từ lượt đầu: cửa sổ mới
        primary.checkFixed(SCOPE, subject, 3, 60);
        assertThat(jdbc.queryForObject("SELECT request_count FROM ha_rate_limit_fixed WHERE scope = ?",
                Integer.class, SCOPE)).isEqualTo(1);
    }

    @Test void slidingWindowIsSharedAndFreesOldestCallFirst() {
        SharedRateLimitStore primary = new SharedRateLimitStore(jdbc, clock);
        SharedRateLimitStore secondary = new SharedRateLimitStore(jdbc, clock);
        primary.checkSliding(SCOPE, subject, 2, 3600);
        clock.advance(Duration.ofMinutes(30));
        secondary.checkSliding(SCOPE, subject, 2, 3600);

        assertThatThrownBy(() -> primary.checkSliding(SCOPE, subject, 2, 3600))
                .isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(30).plusSeconds(1)); // lượt đầu ra khỏi cửa sổ 1 giờ
        secondary.checkSliding(SCOPE, subject, 2, 3600);
    }

    @Test void differentSubjectsHaveSeparateLimits() {
        SharedRateLimitStore store = new SharedRateLimitStore(jdbc, clock);
        store.checkFixed(SCOPE, subject, 1, 60);
        store.checkFixed(SCOPE, UUID.randomUUID().toString(), 1, 60);
        assertThatThrownBy(() -> store.checkFixed(SCOPE, subject, 1, 60)).isInstanceOf(ApiException.class);
    }

    @Test void subjectIsStoredHashedNotInPlainText() {
        new SharedRateLimitStore(jdbc, clock).checkFixed(SCOPE, "203.0.113.7|demo@example.test", 5, 60);
        String stored = jdbc.queryForObject("SELECT subject_hash FROM ha_rate_limit_fixed WHERE scope = ?",
                String.class, SCOPE);
        assertThat(stored).hasSize(64).doesNotContain("203.0.113.7");
    }

    @Test void purgeRemovesRowsOlderThanRetentionOnly() {
        SharedRateLimitStore store = new SharedRateLimitStore(jdbc, clock);
        store.checkFixed(SCOPE, subject, 5, 60);
        store.checkSliding(SCOPE, subject, 5, 3600);
        clock.advance(SharedRateLimitStore.RETENTION.plusMinutes(1));
        String fresh = UUID.randomUUID().toString();
        store.checkSliding(SCOPE, fresh, 5, 3600);

        store.purgeExpired();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ha_rate_limit_fixed WHERE scope = ?",
                Integer.class, SCOPE)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ha_rate_limit_sliding WHERE scope = ?",
                Integer.class, SCOPE)).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
