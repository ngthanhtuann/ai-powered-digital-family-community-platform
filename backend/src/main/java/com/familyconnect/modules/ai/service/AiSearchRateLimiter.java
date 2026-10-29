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

/** Per-user sliding-window limit for search requests. */
@Component
public class AiSearchRateLimiter {
    private final int limit;
    private final Clock clock;
    private final Map<UUID, ArrayDeque<Instant>> requests = new HashMap<>();

    public AiSearchRateLimiter(@Value("${ai.search.limit_per_hour:60}") int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public synchronized void check(UUID userId) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofHours(1));
        requests.values().forEach(history -> {
            while (!history.isEmpty() && !history.peekFirst().isAfter(cutoff)) history.removeFirst();
        });
        requests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        ArrayDeque<Instant> history = requests.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        if (history.size() >= limit) throw ApiException.tooManyRequests(3600);
        history.addLast(now);
    }
}
