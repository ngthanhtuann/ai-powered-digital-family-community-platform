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

    /** BR-AUTH-23: Gia phả gọi khi báo mất hồ sơ có gắn tài khoản; khóa với lý do "Đã mất", thu hồi phiên. */
    void lockDeceasedAccount(UUID userId, UUID actorId);

    /** BR-AUTH-23: báo nhầm; chỉ mở khóa tài khoản đang khóa với lý do "Đã mất". */
    void unlockDeceasedAccount(UUID userId, UUID actorId);

    /**
     * BR-AUTH-25: tài khoản mới nhận vai trò của tài khoản cũ; tài khoản cũ REMOVED, bị khóa, thu hồi phiên; chuyển nội dung công khai.
     * Gia phả đã kiểm tra quyền và tài khoản mới đã xác minh cùng dòng họ.
     */
    void replaceAccount(UUID oldUserId, UUID newUserId, UUID actorId, String reason);

    /** BR-AUTH-26: vợ/chồng ngoài tộc đã ly hôn rời dòng họ (REMOVED, thu hồi phiên); không áp dụng cho trưởng họ. */
    void removeFromFamily(UUID userId, UUID actorId, String reason);

    /** BR-AUTH-26: tài khoản của người được chọn tay làm trưởng chi nhận BRANCH_ADMIN của chi đó; trưởng họ giữ nguyên. */
    void grantBranchHead(UUID userId, UUID familyId, UUID branchId, UUID actorId);
}
