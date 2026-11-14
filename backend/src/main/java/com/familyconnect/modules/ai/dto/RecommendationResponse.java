package com.familyconnect.modules.ai.dto;

import java.util.List;

public record RecommendationResponse(List<SemanticSearchResult> recommendations) { }
