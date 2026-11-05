package com.familyconnect.modules.ai.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.dto.SummaryRequest;
import com.familyconnect.modules.ai.dto.SummaryResponse;
import com.familyconnect.modules.ai.service.AiSummaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/summaries")
public class AiSummaryController {
    private final AiSummaryService service;
    public AiSummaryController(AiSummaryService service) { this.service = service; }
    @PostMapping
    public ApiResponse<SummaryResponse> summarize(@Valid @RequestBody SummaryRequest request) {
        return ApiResponse.ok(service.summarize(CurrentUser.require(), request));
    }
}
