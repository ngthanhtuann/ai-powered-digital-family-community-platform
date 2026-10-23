package com.familyconnect.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Quyền trên tệp theo gia đình sở hữu (conventions.md mục 7.3, BR-COM-08): HERITAGE thì ownerId là familyId,
 * PERSON thì lấy gia đình của thành viên qua GenealogyQuery. SCRUM-46 thay bằng bảng media có family_id.
 */
@Component
public class MediaAccess {

    private final MembershipQuery membershipQuery;
    private final GenealogyQuery genealogyQuery;

    public MediaAccess(MembershipQuery membershipQuery, GenealogyQuery genealogyQuery) {
        this.membershipQuery = membershipQuery;
        this.genealogyQuery = genealogyQuery;
    }

    /** 401 AUTH_002, 403 PERM_002 hoặc PERM_001 nếu người gọi không thuộc gia đình sở hữu; SYSTEM_ADMIN luôn qua. */
    public void requireAccess(String ownerType, UUID ownerId) {
        UUID userId = CurrentUser.require().id();
        membershipQuery.requireFamilyAccess(userId, familyOf(ownerType, ownerId));
    }

    private UUID familyOf(String ownerType, UUID ownerId) {
        String type = ownerType == null ? "" : ownerType.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "HERITAGE" -> ownerId;
            case "PERSON" -> genealogyQuery.findPersonFamilyId(ownerId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
            default -> throw ApiException.invalidField("ownerType", "ownerType chỉ nhận HERITAGE hoặc PERSON");
        };
    }
}
