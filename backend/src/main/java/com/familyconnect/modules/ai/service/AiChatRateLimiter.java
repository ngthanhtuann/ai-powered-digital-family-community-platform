package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AiChatRateLimiter {
    private final int limit;
    private final Clock clock;
    private final Map<UUID, ArrayDeque<Instant>> calls = new HashMap<>();
    public AiChatRateLimiter(@Value("${ai.chat.limit_per_hour:20}") int limit, Clock clock) {
        this.limit = limit; this.clock = clock;
    }
    public synchronized void check(UUID userId) {
        Instant now = clock.instant(), cutoff = now.minus(Duration.ofHours(1));
        calls.values().forEach(q -> { while (!q.isEmpty() && !q.peekFirst().isAfter(cutoff)) q.removeFirst(); });
        calls.entrySet().removeIf(e -> e.getValue().isEmpty());
        ArrayDeque<Instant> q = calls.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        if (limit < 1 || q.size() >= limit) {
            long retry = q.isEmpty() ? 3600 : Duration.between(now, q.peekFirst().plus(Duration.ofHours(1))).toSeconds();
            throw ApiException.tooManyRequests(Math.max(1, retry + 1));
        }
        q.addLast(now);
    }
}
