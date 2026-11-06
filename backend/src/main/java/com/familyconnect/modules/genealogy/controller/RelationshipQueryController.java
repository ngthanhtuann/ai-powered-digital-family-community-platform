package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import com.familyconnect.modules.genealogy.service.KinshipService;
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
public class RelationshipQueryController {

    private final KinshipService kinshipService;

    // Cập nhật Constructor nhận vào KinshipService thay vì RelationshipService cũ
    public RelationshipQueryController(KinshipService kinshipService) {
        this.kinshipService = kinshipService;
    }

    @GetMapping("/relationships")
    @PreAuthorize("hasAnyRole('MEMBER', 'BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Truy vấn quan hệ họ hàng giữa A và B", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"MEMBER\", \"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<RelationshipResponse> getRelationship(
            @RequestParam UUID personA,
            @RequestParam UUID personB,
            Authentication authentication) {

        String userRole = "MEMBER";
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String r = auth.getAuthority().replace("ROLE_", "");
                if ("SYSTEM_ADMIN".equals(r) || "BRANCH_ADMIN".equals(r)) {
                    userRole = r;
                    break;
                }
            }
        }

        // Gọi chính xác phương thức xử lý đồ thị họ hàng từ KinshipService
        RelationshipResponse response = kinshipService.calculateRelationship(personA, personB, userRole);
        return ResponseEntity.ok(response);
    }
}

