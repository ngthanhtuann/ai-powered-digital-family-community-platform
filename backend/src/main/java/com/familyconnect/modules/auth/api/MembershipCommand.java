package com.familyconnect.modules.auth.api;

import java.util.UUID;

/** Thao tác ghi tư cách thành viên mà module khác được gọi (auth.md mục 6.3a). */
public interface MembershipCommand {

    /**
     * Module Gia phả gọi ở UC-GEN-01 bước 4, trong cùng giao dịch tạo family (BR-AUTH-12):
     * tạo bản ghi APPROVED, vai trò BRANCH_ADMIN, quản lý cả gia đình; hủy yêu cầu PENDING đang có.
     * Ném 409 AUTH_005 nếu người dùng đã là thành viên đã xác minh của một gia đình.
     */
    void assignFamilyOwner(UUID userId, UUID familyId);
}
