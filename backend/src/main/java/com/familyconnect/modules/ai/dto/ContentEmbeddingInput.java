package com.familyconnect.modules.ai.dto;

import java.util.UUID;

public record ContentEmbeddingInput(
        UUID sourceId,
        UUID familyId,
        UUID branchId,
        String sourceType,
        String title,
        String content
) {
}