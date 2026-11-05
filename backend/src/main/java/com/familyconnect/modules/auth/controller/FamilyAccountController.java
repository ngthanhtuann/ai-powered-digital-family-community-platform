package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.dto.AdminDtos.FamilyAccount;
import com.familyconnect.modules.auth.dto.AdminDtos.FamilyAccountRoleRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.service.FamilyAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Trưởng họ bổ nhiệm trưởng chi (UC-AUTH-14). Quyền kiểm ở FamilyAccountService. */
@RestController
@RequestMapping("/api/v1/families/{familyId}/accounts")
@Tag(name = "FamilyRoles")
public class FamilyAccountController {

    private final FamilyAccountService service;

    public FamilyAccountController(FamilyAccountService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "listFamilyAccounts", summary = "Tài khoản đã xác minh trong dòng họ (UC-AUTH-14)")
    public PageResponse<FamilyAccount> list(@PathVariable UUID familyId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.require().id(), familyId, page, size);
    }

    @PutMapping("/{userId}")
    @Operation(operationId = "setFamilyAccountRole", summary = "Bổ nhiệm hoặc thu quyền trưởng chi (UC-AUTH-14)")
    public ApiResponse<FamilyAccount> setRole(@PathVariable UUID familyId, @PathVariable UUID userId,
                                              @Valid @RequestBody FamilyAccountRoleRequest request) {
        return ApiResponse.ok(service.setRole(CurrentUser.require().id(), familyId, userId, request));
    }
}
