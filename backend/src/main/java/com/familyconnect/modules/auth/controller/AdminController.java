package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentType;
import com.familyconnect.modules.auth.api.ContentModerationPort.ReportStatus;
import com.familyconnect.modules.auth.dto.AdminDtos.AdminContentReport;
import com.familyconnect.modules.auth.dto.AdminDtos.AdminUserData;
import com.familyconnect.modules.auth.dto.AdminDtos.AuditLogData;
import com.familyconnect.modules.auth.dto.AdminDtos.MembershipRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.GlobalRole;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.AdminDtos.ReviewRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.UserRolesRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.UserStatusRequest;
import com.familyconnect.modules.auth.entity.UserStatus;
import com.familyconnect.modules.auth.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Trang /admin: người dùng (UC-AUTH-08) và kiểm duyệt (UC-AUTH-09), nhật ký (UC-AUTH-10). Quyền SYSTEM_ADMIN kiểm ở AdminService. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    @Operation(operationId = "adminListUsers", summary = "Danh sách người dùng (UC-AUTH-08)")
    public PageResponse<AdminUserData> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) GlobalRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return adminService.listUsers(CurrentUser.require().id(), q, status, role, page, size, sort);
    }

    @GetMapping("/users/{userId}")
    @Operation(operationId = "adminGetUser", summary = "Chi tiết người dùng (UC-AUTH-08)")
    public ApiResponse<AdminUserData> getUser(@PathVariable UUID userId) {
        return ApiResponse.ok(adminService.getUser(CurrentUser.require().id(), userId));
    }

    @PatchMapping("/users/{userId}")
    @Operation(operationId = "adminUpdateUserStatus", summary = "Khóa hoặc mở khóa tài khoản (UC-AUTH-08)")
    public ApiResponse<AdminUserData> updateStatus(@PathVariable UUID userId,
                                                   @Valid @RequestBody UserStatusRequest request) {
        return ApiResponse.ok(adminService.updateStatus(CurrentUser.require().id(), userId, request));
    }

    @PutMapping("/users/{userId}/roles")
    @Operation(operationId = "adminSetUserRoles", summary = "Đổi vai trò toàn hệ thống (UC-AUTH-08)")
    public ApiResponse<AdminUserData> setRoles(@PathVariable UUID userId, @Valid @RequestBody UserRolesRequest request) {
        return ApiResponse.ok(adminService.setRoles(CurrentUser.require().id(), userId, request));
    }

    @PutMapping("/users/{userId}/membership")
    @Operation(operationId = "adminSetUserMembership", summary = "Gán người dùng vào gia đình và vai trò (UC-AUTH-08)")
    public ApiResponse<AdminUserData> setMembership(@PathVariable UUID userId,
                                                    @Valid @RequestBody MembershipRequest request) {
        return ApiResponse.ok(adminService.setMembership(CurrentUser.require().id(), userId, request));
    }

    @GetMapping("/content-reports")
    @Operation(operationId = "adminListContentReports", summary = "Danh sách nội dung bị báo cáo (UC-AUTH-09)")
    public PageResponse<AdminContentReport> listReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ContentType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return adminService.listReports(CurrentUser.require().id(), status, targetType, page, size, sort);
    }

    @PatchMapping("/content-reports/{id}")
    @Operation(operationId = "adminReviewContentReport", summary = "Xử lý báo cáo nội dung (UC-AUTH-09)")
    public ApiResponse<AdminContentReport> review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(adminService.reviewReport(CurrentUser.require().id(), id, request.action()));
    }

    @GetMapping("/audit-logs")
    @Operation(operationId = "adminListAuditLogs", summary = "Xem nhật ký kiểm toán (UC-AUTH-10)")
    public PageResponse<AuditLogData> listAuditLogs(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return adminService.listAuditLogs(CurrentUser.require().id(), actorUserId, action, from, to, page, size, sort);
    }
}
