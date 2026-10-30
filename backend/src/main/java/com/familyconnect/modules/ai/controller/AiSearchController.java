package com.familyconnect.modules.ai.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.dto.SemanticSearchResponse;
import com.familyconnect.modules.ai.service.SemanticSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiSearchController {
    private final SemanticSearchService searchService;

    public AiSearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ApiResponse<SemanticSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID familyId) {
        return ApiResponse.ok(searchService.search(CurrentUser.require(), query, familyId, page, size));
    }
}
