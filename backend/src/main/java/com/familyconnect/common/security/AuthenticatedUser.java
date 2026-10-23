package com.familyconnect.common.security;

import java.util.List;
import java.util.UUID;

/**
 * Người đang gọi API, lấy từ access token.
 * {@code roles} là vai trò toàn hệ thống (MEMBER, SYSTEM_ADMIN); BRANCH_ADMIN và trạng thái xác minh
 * tra ở MembershipQuery mỗi request (auth.md mục 4.2).
 */
public record AuthenticatedUser(UUID id, List<String> roles) {

    public boolean isSystemAdmin() {
        return roles.contains("SYSTEM_ADMIN");
    }
}
