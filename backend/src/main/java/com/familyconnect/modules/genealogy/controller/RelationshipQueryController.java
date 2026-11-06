package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import com.familyconnect.modules.genealogy.service.KinshipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** GET /relationships theo genealogy.yaml (UC-GEN-08); quyền kiểm ở service. */
@RestController
@RequestMapping("/api/v1")
public class RelationshipQueryController {

    private final KinshipService kinshipService;

    public RelationshipQueryController(KinshipService kinshipService) {
        this.kinshipService = kinshipService;
    }

    @GetMapping("/relationships")
    public ApiResponse<RelationshipResponse> relationship(@RequestParam UUID personA, @RequestParam UUID personB) {
        return ApiResponse.ok(kinshipService.relationship(personA, personB));
    }
}
