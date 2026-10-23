package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Quyền của module Gia phả (BR-GEN-14): đọc thì là thành viên đã xác minh của gia đình;
 * sửa thì là BRANCH_ADMIN trong phạm vi chi (branch_id NULL là cả gia đình) hoặc SYSTEM_ADMIN.
 */
@Component
public class GenealogyAccess {

    private final MembershipQuery membershipQuery;
    private final BranchRepository branchRepository;

    public GenealogyAccess(MembershipQuery membershipQuery, BranchRepository branchRepository) {
        this.membershipQuery = membershipQuery;
        this.branchRepository = branchRepository;
    }

    public record Viewer(UUID userId, MembershipView membership) {
        public boolean isSystemAdmin() {
            return membership.role() == EffectiveRole.SYSTEM_ADMIN;
        }

        /** BRANCH_ADMIN đã xác minh của chính gia đình này, hoặc SYSTEM_ADMIN: thấy đủ dữ liệu (BR-GEN-10). */
        public boolean seesFullData(UUID familyId) {
            return isSystemAdmin() || (membership.role() == EffectiveRole.BRANCH_ADMIN && membership.verified()
                    && familyId.equals(membership.familyId()));
        }
    }

    public Viewer current() {
        UUID userId = CurrentUser.require().id();
        return new Viewer(userId, membershipQuery.getMembership(userId));
    }

    /** 403 PERM_002 nếu chưa xác minh, PERM_001 nếu gia đình khác; SYSTEM_ADMIN luôn qua. */
    public Viewer requireRead(UUID familyId) {
        Viewer viewer = current();
        membershipQuery.requireFamilyAccess(viewer.userId(), familyId);
        return viewer;
    }

    /** Đối tượng ở chi branchId (NULL là cấp gia đình): chỉ người quản lý chi đó hoặc cả gia đình mới được sửa. */
    public void requireManage(Viewer viewer, UUID familyId, UUID branchId) {
        if (!canManage(viewer, familyId, branchId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    public boolean canManage(Viewer viewer, UUID familyId, UUID branchId) {
        if (viewer.isSystemAdmin()) {
            return true;
        }
        MembershipView m = viewer.membership();
        if (m.role() != EffectiveRole.BRANCH_ADMIN || !m.verified() || !familyId.equals(m.familyId())) {
            return false;
        }
        if (m.branchId() == null) {
            return true;
        }
        return branchId != null && branchRepository.isWithin(familyId, branchId, m.branchId());
    }
}
