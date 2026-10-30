package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.modules.genealogy.dto.FamilyTreeResponse;
import com.familyconnect.modules.genealogy.service.GenealogyTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class GenealogyTreeController {

    private final GenealogyTreeService genealogyTreeService;

    public GenealogyTreeController(GenealogyTreeService genealogyTreeService) {
        this.genealogyTreeService = genealogyTreeService;
    }

    @GetMapping("/families/{familyId}/tree")
    @PreAuthorize("hasAnyRole('MEMBER', 'BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Lấy dữ liệu cây gia phả", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"MEMBER\", \"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<FamilyTreeResponse> getFamilyTree(
            @PathVariable UUID familyId,
            @RequestParam(required = false) UUID rootId,
            @RequestParam(defaultValue = "5") int depth,
            Authentication authentication) {
        
        // Trích xuất Role cao nhất hiện tại của User đăng nhập để đưa vào Service xử lý Privacy
        String userRole = "MEMBER";
        if (authentication != null) {
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String role = authority.getAuthority().replace("ROLE_", "");
                if ("SYSTEM_ADMIN".equals(role) || "BRANCH_ADMIN".equals(role)) {
                    userRole = role;
                    break;
                }
            }
        }

        FamilyTreeResponse response = genealogyTreeService.getTree(familyId, rootId, depth, userRole);
        return ResponseEntity.ok(response);
    }
}

