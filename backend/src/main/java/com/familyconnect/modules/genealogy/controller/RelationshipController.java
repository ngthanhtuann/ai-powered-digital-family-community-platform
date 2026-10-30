package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.modules.genealogy.dto.MarriageInput;
import com.familyconnect.modules.genealogy.dto.MarriageStatusInput;
import com.familyconnect.modules.genealogy.dto.ParentChildInput;
import com.familyconnect.modules.genealogy.entity.Marriage;
import com.familyconnect.modules.genealogy.entity.ParentChild;
import com.familyconnect.modules.genealogy.service.RelationshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    private UUID getActorId(Principal principal) {
        if (principal != null && principal.getName() != null) {
            try {
                return UUID.fromString(principal.getName());
            } catch (IllegalArgumentException e) {
                // Fallback nếu principal name không phải UUID
            }
        }
        return UUID.randomUUID(); // Fallback an toàn cho hệ thống
    }

    @PostMapping("/persons/{personId}/parents")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Thêm cha mẹ", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<ParentChild> addParent(
            @PathVariable UUID personId,
            @Valid @RequestBody ParentChildInput input,
            Principal principal) {
        ParentChild result = relationshipService.addParent(personId, input, getActorId(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/parent-child/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Xóa quan hệ cha mẹ con", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<Map<String, Object>> removeParentChild(
            @PathVariable UUID id,
            Principal principal) {
        relationshipService.removeParentChild(id, getActorId(principal));
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/marriages")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Thêm quan hệ hôn nhân", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<Marriage> addMarriage(
            @Valid @RequestBody MarriageInput input,
            Principal principal) {
        Marriage result = relationshipService.addMarriage(input, getActorId(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/marriages/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Cập nhật trạng thái hôn nhân", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<Marriage> updateMarriage(
            @PathVariable UUID id,
            @Valid @RequestBody MarriageStatusInput input,
            Principal principal) {
        Marriage result = relationshipService.updateMarriageStatus(id, input, getActorId(principal));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/marriages/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Xóa quan hệ hôn nhân", extensions = {
        @Extension(name = "x-roles", properties = {
            @ExtensionProperty(name = "roles", value = "[\"BRANCH_ADMIN\", \"SYSTEM_ADMIN\"]")
        })
    })
    public ResponseEntity<Map<String, Object>> removeMarriage(
            @PathVariable UUID id,
            Principal principal) {
        relationshipService.removeMarriage(id, getActorId(principal));
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        return ResponseEntity.ok(response);
    }
}

