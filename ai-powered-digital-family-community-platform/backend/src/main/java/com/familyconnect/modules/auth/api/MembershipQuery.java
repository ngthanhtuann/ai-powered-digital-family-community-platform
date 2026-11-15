package com.familyconnect.modules.auth.api;

import java.util.List;
import java.util.UUID;

/**
 * Module khác hỏi tư cách thành viên của người dùng qua đây (auth.md mục 6.3a), không đọc bảng
 * member_verification trực tiếp.
 */
public interface MembershipQuery {

    /** Tư cách hiện tại; chưa có gia đình thì verified = false, familyId = null. */
    MembershipView getMembership(UUID userId);

    /** Ném 403 PERM_002 nếu chưa xác minh; SYSTEM_ADMIN luôn qua. */
    void requireVerified(UUID userId);

    /** 403 PERM_002 nếu chưa xác minh, 403 PERM_001 nếu khác gia đình; SYSTEM_ADMIN luôn qua. */
    void requireFamilyAccess(UUID userId, UUID familyId);

    /** Người dùng đã xác minh của gia đình (ví dụ để gửi thông báo cho cả gia đình). */
    List<UUID> findVerifiedUserIds(UUID familyId);
}
