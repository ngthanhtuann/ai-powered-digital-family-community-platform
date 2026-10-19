package com.familyconnect.modules.auth.service;

import com.familyconnect.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn đăng nhập N lần mỗi phút theo cặp (IP, email) (BR-AUTH-04, conventions.md mục 7.1).
 * Cửa sổ cố định 60 giây, lưu trong bộ nhớ của từng bản backend: chạy 2 bản sau Nginx thì giới hạn
 * thực tế gấp đôi (đã ghi ở BR-AUTH-04).
 */
@Component
public class LoginRateLimiter {

    private static final long WINDOW_SECONDS = 60;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.auth.login-rate-limit-per-minute:5}") int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** Ghi nhận một lần thử; ném 429 RATE_001 kèm Retry-After nếu vượt giới hạn. */
    public void check(String ip, String email) {
        long now = clock.instant().getEpochSecond();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_SECONDS);
        }
        String key = ip + "|" + email;
        Window window = windows.compute(key, (k, w) ->
                (w == null || now - w.start >= WINDOW_SECONDS) ? new Window(now, 1) : new Window(w.start, w.count + 1));
        if (window.count > limit) {
            long retryAfter = Math.max(1, WINDOW_SECONDS - (now - window.start));
            throw ApiException.tooManyRequests(retryAfter);
        }
    }

    private record Window(long start, int count) {
    }
}
