package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
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
    private final SystemConfigQuery config;
    private final Clock clock;
    private final Map<UUID, ArrayDeque<Instant>> calls = new HashMap<>();
    /** Giới hạn đọc từ cấu hình hệ thống ai.chat.limit_per_hour mỗi lần gọi (SCRUM-94, UC-AUTH-12). */
    public AiChatRateLimiter(SystemConfigQuery config, Clock clock) {
        this.config = config; this.clock = clock;
    }
    public synchronized void check(UUID userId) {
        int limit = config.getInt(SystemConfigQuery.AI_CHAT_LIMIT_PER_HOUR);
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
