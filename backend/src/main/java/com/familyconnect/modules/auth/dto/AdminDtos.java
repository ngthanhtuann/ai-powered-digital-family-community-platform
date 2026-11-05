package com.familyconnect.modules.auth.dto;

import com.familyconnect.modules.auth.api.ContentModerationPort.ContentReportView;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentType;
import com.familyconnect.modules.auth.api.ContentModerationPort.ReportStatus;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.entity.MemberRole;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request và response của /admin/* (auth.yaml, UC-AUTH-08, 09). */
public final class AdminDtos {

    private AdminDtos() {
    }

    public enum GlobalRole { MEMBER, SYSTEM_ADMIN }

    public enum ReviewAction { HIDE_CONTENT, DISMISS, RESTORE_CONTENT }

    /** Schema AdminUserStatusRequest: lockReason bắt buộc khi LOCKED (kiểm ở service). */
    public record UserStatusRequest(@NotNull UserStatus status, @Size(max = 255) String lockReason) {
    }

    /** Schema AdminUserRolesRequest: ghi đè, luôn có MEMBER (kiểm ở service). */
    public record UserRolesRequest(@NotEmpty List<@NotNull GlobalRole> roles) {
    }

    /** Schema AdminMembershipRequest (BR-AUTH-14). */
    public record MembershipRequest(@NotNull UUID familyId, UUID branchId, @NotNull MemberRole role) {
    }

    /** Schema FamilyAccountRoleRequest (UC-AUTH-14). */
    public record FamilyAccountRoleRequest(@NotNull MemberRole role, UUID branchId) {
    }

    /** Schema FamilyAccount: không có email, số điện thoại (BR-AUTH-22). */
    public record FamilyAccount(UUID userId, String fullName, MemberRole role, UUID branchId, String branchName) {
    }

    /** Schema ContentReport của auth.yaml: một dòng hàng đợi (BR-AUTH-15). */
    public record AdminContentReport(UUID id, ContentType targetType, UUID targetId, UUID familyId, String familyName,
                                     UUID reporterId, String reason, String contentSnippet, String contentOwnerName,
                                     int reportCount, ReportStatus status, OffsetDateTime createdAt,
                                     OffsetDateTime updatedAt) {
        public static AdminContentReport of(ContentReportView v, String familyName, String ownerName) {
            return new AdminContentReport(v.id(), v.targetType(), v.targetId(), v.familyId(), familyName, v.reporterId(),
                    v.reason(), v.contentSnippet(), ownerName, v.reportCount(), v.status(), v.createdAt(), v.updatedAt());
        }
    }

    /** Schema ContentReportReviewRequest. */
    public record ReviewRequest(@NotNull ReviewAction action) {
    }

    /** Schema AdminUser. Không chứa mật khẩu hay token. */
    public record AdminUserData(
            UUID id,
            String email,
            String fullName,
            String phone,
            LocalDate dateOfBirth,
            List<String> roles,
            UserStatus status,
            String lockReason,
            OffsetDateTime lastLoginAt,
            MembershipData membership,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static AdminUserData of(User user, List<String> roles, MembershipData membership) {
            return new AdminUserData(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                    user.getDateOfBirth(), roles, user.getStatus(), user.getLockReason(), user.getLastLoginAt(),
                    membership, user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    public record Meta(int page, int size, long total) {
    }

    /** Danh sách phân trang: {"success": true, "data": [...], "meta": {...}} (conventions.md mục 2.1). */
    public record PageResponse<T>(boolean success, List<T> data, Meta meta) {
        public static <T> PageResponse<T> of(List<T> data, int page, int size, long total) {
            return new PageResponse<>(true, data, new Meta(page, size, total));
        }
    }
}
