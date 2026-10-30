package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.TreeData;
import com.familyconnect.modules.genealogy.service.FamilyTreeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** GET /families/{familyId}/tree theo genealogy.yaml (UC-GEN-06); quyền kiểm ở service. */
@RestController
@RequestMapping("/api/v1")
public class FamilyTreeController {

    private final FamilyTreeService treeService;

    public FamilyTreeController(FamilyTreeService treeService) {
        this.treeService = treeService;
    }

    @GetMapping("/families/{familyId}/tree")
    public ApiResponse<TreeData> tree(@PathVariable UUID familyId,
                                      @RequestParam(required = false) UUID rootId,
                                      @RequestParam(required = false) Integer depth) {
        return ApiResponse.ok(treeService.tree(familyId, rootId, depth));
    }
}
