package com.familyconnect.modules.ai.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.dto.RelationshipExplanationRequest;
import com.familyconnect.modules.ai.dto.RelationshipExplanationResponse;
import com.familyconnect.modules.ai.service.RelationshipExplanationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SCRUM-64: POST /ai/relationship-explanations (ai.yaml explainRelationship). */
@RestController
@RequestMapping("/api/v1/ai")
public class AiRelationshipExplanationController {
    private final RelationshipExplanationService service;

    public AiRelationshipExplanationController(RelationshipExplanationService service) {
        this.service = service;
    }

    @PostMapping("/relationship-explanations")
    public ApiResponse<RelationshipExplanationResponse> explain(@Valid @RequestBody RelationshipExplanationRequest request) {
        return ApiResponse.ok(service.explain(CurrentUser.require(), request));
    }
}
