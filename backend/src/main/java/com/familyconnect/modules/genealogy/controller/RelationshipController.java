package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.MarriageInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.MarriageUpdate;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.ParentInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.MarriageData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.ParentChildData;
import com.familyconnect.modules.genealogy.service.RelationshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Quan hệ cha-mẹ-con và hôn nhân theo genealogy.yaml (UC-GEN-04, UC-GEN-05); quyền kiểm ở service. */
@RestController
@RequestMapping("/api/v1")
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping("/persons/{personId}/parents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ParentChildData> addParent(@PathVariable UUID personId, @Valid @RequestBody ParentInput input) {
        return ApiResponse.ok(relationshipService.addParent(personId, input));
    }

    @DeleteMapping("/persons/{personId}/parents/{parentId}")
    public ApiResponse<Void> removeParent(@PathVariable UUID personId, @PathVariable UUID parentId) {
        relationshipService.removeParent(personId, parentId);
        return ApiResponse.empty();
    }

    @PostMapping("/marriages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MarriageData> createMarriage(@Valid @RequestBody MarriageInput input) {
        return ApiResponse.ok(relationshipService.addMarriage(input));
    }

    @PutMapping("/marriages/{marriageId}")
    public ApiResponse<MarriageData> updateMarriage(@PathVariable UUID marriageId,
                                                    @Valid @RequestBody MarriageUpdate input) {
        return ApiResponse.ok(relationshipService.updateMarriage(marriageId, input));
    }

    @DeleteMapping("/marriages/{marriageId}")
    public ApiResponse<Void> deleteMarriage(@PathVariable UUID marriageId) {
        relationshipService.deleteMarriage(marriageId);
        return ApiResponse.empty();
    }
}
