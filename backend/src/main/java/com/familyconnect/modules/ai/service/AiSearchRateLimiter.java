package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.familyconnect.common.ha.SharedRateLimitStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Giới hạn tìm kiếm theo người dùng, cửa sổ trượt 1 giờ (conventions.md mục 7.1).
 * shortcut: bộ đếm nằm trong bộ nhớ từng bản backend, giống LoginRateLimiter; với 2 bản (NFR-10) hạn mức
 * thực tế gấp đôi. Chuyển sang bộ đếm dùng chung (bảng PostgreSQL) nếu cần giới hạn chính xác trên nhiều bản.
 */
@Component
public class AiSearchRateLimiter {
    private final int limit;
    private final Clock clock;
    private final Map<UUID, ArrayDeque<Instant>> requests = new HashMap<>();
    @Autowired
    private SharedRateLimitStore sharedStore;
    @Value("${app.ha.shared-rate-limits:false}")
    private boolean sharedRateLimits;

    public AiSearchRateLimiter(@Value("${ai.search.limit_per_hour:30}") int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public void check(UUID userId) {
        if (sharedRateLimits) {
            sharedStore.checkSliding("SEARCH", userId.toString(), limit, 3600);
            return;
        }
        checkLocal(userId);
    }

    /** Bộ đếm trong JVM (một backend); nhánh dùng chung ở trên không cần khóa JVM vì PostgreSQL đã khóa theo chủ thể. */
    private synchronized void checkLocal(UUID userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofHours(1));
        requests.values().forEach(history -> {
            while (!history.isEmpty() && !history.peekFirst().isAfter(cutoff)) history.removeFirst();
        });
        requests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        ArrayDeque<Instant> history = requests.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        if (history.size() >= limit) {
            // Retry-After: số giây tới khi lượt cũ nhất ra khỏi cửa sổ.
            long wait = Duration.between(now, history.peekFirst().plus(Duration.ofHours(1))).toSeconds();
            throw ApiException.tooManyRequests(Math.max(1, wait + 1));
        }
        history.addLast(now);
    }
}
