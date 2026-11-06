package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.BackupDtos.BackupFile;
import com.familyconnect.modules.auth.dto.BackupDtos.RestoreCreateRequest;
import com.familyconnect.modules.auth.dto.BackupDtos.RestoreResult;
import com.familyconnect.modules.auth.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sao lưu và khôi phục dữ liệu, chỉ SYSTEM_ADMIN (UC-AUTH-11). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Backup")
public class BackupController {
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping("/backups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "adminCreateBackup", summary = "Tạo bản sao lưu PostgreSQL và uploads")
    public ApiResponse<BackupFile> createBackup() {
        return ApiResponse.ok(backupService.createBackup(CurrentUser.require().id()));
    }

    @GetMapping("/backups")
    @Operation(operationId = "adminListBackups", summary = "Danh sách các bản sao lưu")
    public PageResponse<BackupFile> listBackups(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return backupService.listBackups(CurrentUser.require().id(), page, size);
    }

    @PostMapping("/restores")
    @Operation(operationId = "adminCreateRestore", summary = "Khôi phục dữ liệu (ghi đè, yêu cầu RESTORE)")
    public ApiResponse<RestoreResult> restore(@Valid @RequestBody RestoreCreateRequest request) {
        return ApiResponse.ok(backupService.restore(CurrentUser.require().id(), request));
    }
}
