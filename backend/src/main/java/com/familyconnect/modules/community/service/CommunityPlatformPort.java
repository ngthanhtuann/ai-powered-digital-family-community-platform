package com.familyconnect.modules.community.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Những gì Community cần từ module khác (auth, genealogy, ai, audit). Bản cài đặt: {@link ModulePlatformAdapter}.
 * Tách ra để CommunityService kiểm thử được mà không cần các module kia.
 */
public interface CommunityPlatformPort {
    enum Role { MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN }

    /** branchId: chi của thành viên, hoặc chi quản lý của BRANCH_ADMIN (null là cả gia đình). */
    record Actor(UUID userId, UUID familyId, UUID branchId, Role role, boolean verified) {}

    Actor requireAuthenticatedActor();
    boolean branchExists(UUID familyId, UUID branchId);
    /** BR-COM-03: nội dung của chi X hiển thị cho thành viên chi X và chi con của X. */
    boolean canRead(Actor actor, UUID familyId, UUID branchId);
    void requireCanRead(Actor actor, UUID familyId, UUID branchId);
    void requireCanPublishInBranch(Actor actor, UUID familyId, UUID branchId);
    /** BR-COM-05, BR-COM-06: BRANCH_ADMIN trong phạm vi chi mình quản lý, hoặc SYSTEM_ADMIN. */
    boolean canModerate(Actor actor, UUID familyId, UUID branchId);
    void requireCanModerate(Actor actor, UUID familyId, UUID branchId);
    /** Họ tên theo userId; người đã xóa tài khoản có tên mặc định. */
    Map<UUID, String> authorNames(Collection<UUID> userIds);
    /**
     * BR-COM-13: tài khoản có bài "con cháu của tôi" mà actor xem được (chính mình và tổ tiên của mình, của vợ/chồng);
     * null nghĩa là không giới hạn (SYSTEM_ADMIN kiểm duyệt).
     */
    default java.util.Set<UUID> descendantsAudienceAuthors(Actor actor) {
        return actor.role() == Role.SYSTEM_ADMIN ? null : java.util.Set.of(actor.userId());
    }
    /** SCRUM-121: người đăng có hồ sơ gia phả không (mới chọn được "con cháu của tôi"). */
    default boolean hasGenealogyProfile(UUID userId) {
        return false;
    }
    /** SCRUM-117: hồ sơ gia phả, ảnh đại diện của người đăng để bảng tin dẫn tới trang cá nhân. */
    default Map<UUID, com.familyconnect.modules.auth.api.GenealogyPort.PersonCard> authorPersons(Collection<UUID> userIds) {
        return Map.of();
    }
    /** BR-COM-10, BR-COM-12: mọi thao tác xóa nội dung đều ghi audit log. */
    void logContentDeleted(String contentType, UUID contentId, UUID familyId, boolean byOwner);
    /** SCRUM-57: tạo, sửa nội dung cũng ghi audit log (auth.md mục 6.4, nhóm "Module khác"). */
    void logContentSaved(boolean created, String contentType, UUID contentId, UUID familyId);
    /** BR-COM-11: báo module ai cập nhật chỉ mục sau khi giao dịch commit. */
    void postChanged(UUID postId, UUID familyId);
}
