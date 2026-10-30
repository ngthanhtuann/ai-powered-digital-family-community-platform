package com.familyconnect.modules.ai.dto;

import java.util.List;

public record SemanticSearchResponse(String query, List<SemanticSearchResult> results,
                                    int page, int size, long total, String message) {
}
