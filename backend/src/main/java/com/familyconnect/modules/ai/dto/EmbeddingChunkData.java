package com.familyconnect.modules.ai.dto;

import java.util.UUID;

public record EmbeddingChunkData(
        UUID familyId,
        UUID branchId,
        String sourceType,
        UUID sourceId,
        int chunkNo,
        String content,
        String contentHash,
        float[] embedding
) {
}