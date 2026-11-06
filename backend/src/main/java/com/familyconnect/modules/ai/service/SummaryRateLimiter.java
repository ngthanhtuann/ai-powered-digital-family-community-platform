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
public class SummaryRateLimiter {
    private final int limit;
    private final Clock clock;
    private final Map<UUID, ArrayDeque<Instant>> calls = new HashMap<>();
    public SummaryRateLimiter(@Value("${ai.summary.limit_per_hour:10}") int limit, Clock clock) {
        this.limit = limit; this.clock = clock;
    }
    public synchronized void check(UUID userId) {
        Instant now = clock.instant(), cutoff = now.minus(Duration.ofHours(1));
        ArrayDeque<Instant> history = calls.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        while (!history.isEmpty() && !history.peekFirst().isAfter(cutoff)) history.removeFirst();
        if (history.size() >= limit) throw ApiException.tooManyRequests(
                Math.max(1, Duration.between(now, history.peekFirst().plus(Duration.ofHours(1))).toSeconds() + 1));
        history.addLast(now);
    }
}
