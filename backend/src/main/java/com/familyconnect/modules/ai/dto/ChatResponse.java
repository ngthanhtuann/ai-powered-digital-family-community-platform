package com.familyconnect.modules.ai.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(UUID sessionId, UUID messageId, String answer, List<SourceReference> sources,
                           long latencyMs) {
    public ChatResponse { sources = List.copyOf(sources); }
    public record SourceReference(String sourceType, UUID sourceId) { }
}
