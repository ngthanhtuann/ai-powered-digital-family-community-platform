package com.familyconnect.common.ha;

import com.familyconnect.common.exception.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

/**
 * SCRUM-96: bộ đếm RATE_001 dùng chung trong PostgreSQL.
 * REQUIRES_NEW đảm bảo lượt gọi được lưu dù transaction đăng nhập thất bại.
 * Dữ liệu chủ thể được băm SHA-256, không lưu IP/email ở dạng rõ.
 */
@Component
public class SharedRateLimitStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SharedRateLimitStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Fixed window kể từ request đầu, phù hợp Login/RefreshRateLimiter hiện hành. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkFixed(String scope, String subject, int limit, long seconds) {
        if (limit < 1) throw ApiException.tooManyRequests(seconds);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        OffsetDateTime resetBefore = now.minusSeconds(seconds);
        var window = jdbc.queryForObject("""
                INSERT INTO ha_rate_limit_fixed(scope, subject_hash, window_started_at, request_count)
                VALUES(?, ?, ?, 1)
                ON CONFLICT (scope, subject_hash) DO UPDATE SET
                window_started_at = CASE WHEN ha_rate_limit_fixed.window_started_at <= ?
                    THEN EXCLUDED.window_started_at ELSE ha_rate_limit_fixed.window_started_at END,
                request_count = CASE WHEN ha_rate_limit_fixed.window_started_at <= ?
                    THEN 1 ELSE ha_rate_limit_fixed.request_count + 1 END
                RETURNING window_started_at, request_count
                """, (rs, row) -> new FixedWindow(
                rs.getObject("window_started_at", OffsetDateTime.class), rs.getInt("request_count")),
                scope, hash(subject), now, resetBefore, resetBefore);
        if (window != null && window.count() > limit) {
            long retry = Duration.between(now, window.start().plusSeconds(seconds)).toSeconds();
            throw ApiException.tooManyRequests(Math.max(1, retry));
        }
    }

    /** Sliding window 1 giờ cho AI, serialize theo scope + chủ thể bằng khóa advisory PostgreSQL. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkSliding(String scope, String subject, int limit, long seconds) {
        if (limit < 1) throw ApiException.tooManyRequests(seconds);
        String hashed = hash(subject);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusSeconds(seconds);
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {}, scope + ":" + hashed);
        jdbc.update("DELETE FROM ha_rate_limit_sliding WHERE scope=? AND subject_hash=? AND called_at <= ?",
                scope, hashed, cutoff);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ha_rate_limit_sliding WHERE scope=? AND subject_hash=?",
                Long.class, scope, hashed);
        if (count != null && count >= limit) {
            OffsetDateTime oldest = jdbc.queryForObject(
                    "SELECT MIN(called_at) FROM ha_rate_limit_sliding WHERE scope=? AND subject_hash=?",
                    OffsetDateTime.class, scope, hashed);
            long remaining = oldest == null ? seconds : Duration.between(now, oldest.plusSeconds(seconds)).toSeconds() + 1;
            throw ApiException.tooManyRequests(Math.max(1, remaining));
        }
        jdbc.update("INSERT INTO ha_rate_limit_sliding(scope,subject_hash,called_at) VALUES(?,?,?)",
                scope, hashed, now);
    }

    private record FixedWindow(OffsetDateTime start, int count) {}

    private static String hash(String subject) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256", e);
        }
    }
}
