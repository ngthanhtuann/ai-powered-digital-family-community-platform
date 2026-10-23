package com.familyconnect.modules.auth.api;

import java.util.UUID;

/**
 * Tư cách thành viên của một người dùng.
 * {@code branchId}: với BRANCH_ADMIN là chi quản lý (null là cả gia đình); familyId null với SYSTEM_ADMIN.
 */
public record MembershipView(UUID userId, UUID familyId, UUID branchId, EffectiveRole role, boolean verified) {
}
