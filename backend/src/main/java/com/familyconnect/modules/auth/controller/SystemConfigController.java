package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.SystemConfigDtos.ConfigUpdateRequest;
import com.familyconnect.modules.auth.dto.SystemConfigDtos.SystemConfigData;
import com.familyconnect.modules.auth.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cấu hình hệ thống, chỉ SYSTEM_ADMIN (UC-AUTH-12). */
@RestController
@RequestMapping("/api/v1/admin/configs")
@Tag(name = "Config")
public class SystemConfigController {
    /** Số khóa nhỏ nên không phân trang; meta.size cố định như ví dụ auth.yaml. */
    private static final int LIST_SIZE = 100;

    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "adminListConfigs", summary = "Danh sách cấu hình hệ thống (UC-AUTH-12)")
    public PageResponse<SystemConfigData> list() {
        List<SystemConfigData> items = service.list(CurrentUser.require().id());
        return PageResponse.of(items, 0, LIST_SIZE, items.size());
    }

    @PutMapping("/{key}")
    @Operation(operationId = "adminUpdateConfig", summary = "Đổi giá trị cấu hình (UC-AUTH-12)")
    public ApiResponse<SystemConfigData> update(@PathVariable String key,
                                                @Valid @RequestBody ConfigUpdateRequest request) {
        return ApiResponse.ok(service.update(CurrentUser.require().id(), key, request.value()));
    }
}
