package com.familyconnect.modules.ai.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.dto.RecommendationResponse;
import com.familyconnect.modules.ai.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/recommendations")
public class AiRecommendationController {
    private final RecommendationService service;
    public AiRecommendationController(RecommendationService service) { this.service = service; }
    @GetMapping
    public ApiResponse<RecommendationResponse> recommend(@RequestParam String contextType,
            @RequestParam UUID contextId, @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) UUID familyId) {
        return ApiResponse.ok(service.recommend(CurrentUser.require(), contextType, contextId, familyId, limit));
    }
}
