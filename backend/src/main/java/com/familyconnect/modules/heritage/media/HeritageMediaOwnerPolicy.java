package com.familyconnect.modules.heritage.media;

import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.heritage.repository.HeritageDocumentRepository;
import com.familyconnect.modules.community.api.MediaOwnerPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** HERITAGE: ownerId là familyId theo luồng upload hiện có của Heritage. */
@Component
public class HeritageMediaOwnerPolicy implements MediaOwnerPolicy {
    private final MembershipQuery membership;
    private final HeritageDocumentRepository documents;

    public HeritageMediaOwnerPolicy(MembershipQuery membership, HeritageDocumentRepository documents) {
        this.membership = membership;
        this.documents = documents;
    }

    @Override public String ownerType() { return "HERITAGE"; }

    @Override public UUID requireUploadFamily(UUID ownerId) {
        membership.requireFamilyAccess(CurrentUser.require().id(), ownerId);
        return ownerId;
    }

    @Override public void requireRead(UUID ownerId, UUID familyId) {
        if (!ownerId.equals(familyId)) throw new ApiException(ErrorCode.PERM_001);
        membership.requireFamilyAccess(CurrentUser.require().id(), familyId);
    }

    /** Người tải lên, hoặc BRANCH_ADMIN, SYSTEM_ADMIN (cùng quyền quản lý tài liệu lịch sử của HeritageService). */
    @Override public void requireDelete(UUID mediaId, UUID ownerId, UUID familyId, boolean uploader) {
        requireRead(ownerId, familyId);
        EffectiveRole role = membership.getMembership(CurrentUser.require().id()).role();
        if (!uploader && role != EffectiveRole.BRANCH_ADMIN && role != EffectiveRole.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (documents.existsByMediaIdAndDeletedAtIsNull(mediaId)) throw new ApiException(ErrorCode.CONFLICT_001);
    }
}
