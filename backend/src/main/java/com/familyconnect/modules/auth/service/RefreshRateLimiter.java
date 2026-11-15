package com.familyconnect.modules.auth.service;

import com.familyconnect.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.familyconnect.common.ha.SharedRateLimitStore;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Giới hạn /auth/refresh theo IP, không ghi hoặc lưu refresh token. Bộ đếm thuộc từng backend. */
@Component
public class RefreshRateLimiter {

    private static final long WINDOW_SECONDS = 60;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    @Autowired
    private SharedRateLimitStore sharedStore;
    @Value("${app.ha.shared-rate-limits:false}")
    private boolean sharedRateLimits;

    public RefreshRateLimiter(@Value("${app.auth.refresh-rate-limit-per-minute:20}") int limit, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("AUTH_REFRESH_RATE_LIMIT_PER_MINUTE must be positive");
        }
        this.limit = limit;
        this.clock = clock;
    }

    public void check(String ip) {
        if (sharedRateLimits) {
            sharedStore.checkFixed("REFRESH", ip, limit, WINDOW_SECONDS);
            return;
        }
        long now = clock.instant().getEpochSecond();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_SECONDS);
        }
        Window window = windows.compute(ip, (key, current) ->
                current == null || now - current.start() >= WINDOW_SECONDS
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        if (window.count() > limit) {
            throw ApiException.tooManyRequests(Math.max(1, WINDOW_SECONDS - (now - window.start())));
        }
    }

    private record Window(long start, int count) {
    }
}
