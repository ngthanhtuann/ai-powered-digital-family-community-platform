package com.familyconnect.modules.ai.dto;

import java.util.UUID;

public record SemanticSearchResult(String sourceType, UUID sourceId, String title,
                                   String snippet, double score, String path) {
}
