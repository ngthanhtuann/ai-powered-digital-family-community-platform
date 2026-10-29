package com.familyconnect.modules.heritage.media;

import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.community.api.MediaOwnerPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** HERITAGE: ownerId là familyId theo luồng upload hiện có của Heritage. */
@Component
public class HeritageMediaOwnerPolicy implements MediaOwnerPolicy {
    private final MembershipQuery membership;

    public HeritageMediaOwnerPolicy(MembershipQuery membership) { this.membership = membership; }

    @Override public String ownerType() { return "HERITAGE"; }

    @Override public UUID requireUploadFamily(UUID ownerId) {
        membership.requireFamilyAccess(CurrentUser.require().id(), ownerId);
        return ownerId;
    }

    @Override public void requireRead(UUID ownerId, UUID familyId) {
        if (!ownerId.equals(familyId)) throw new ApiException(ErrorCode.PERM_001);
        membership.requireFamilyAccess(CurrentUser.require().id(), familyId);
    }
}
