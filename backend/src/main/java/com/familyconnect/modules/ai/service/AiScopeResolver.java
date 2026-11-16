package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phạm vi dữ liệu người gọi được dùng cho tìm kiếm, chat, gợi ý (BR-AI-02, BR-AI-13; conventions.md mục 6.2).
 * Một chỗ duy nhất quyết định gia đình, vai trò, chi được xem và hồ sơ trẻ em cần ẩn.
 */
@Component
public class AiScopeResolver {

    /** Gia đình, vai trò và chi của người gọi; {@code actorBranchId} null là chưa thuộc chi hoặc quản trị. */
    public record AiScope(UUID familyId, EffectiveRole role, UUID actorBranchId) {
        /** SYSTEM_ADMIN và trưởng chi toàn gia đình thấy mọi chi. */
        public boolean allBranches() {
            return role == EffectiveRole.SYSTEM_ADMIN || (role == EffectiveRole.BRANCH_ADMIN && actorBranchId == null);
        }
    }

    private final MembershipQuery memberships;
    private final GenealogyQuery genealogy;
    private final EmbeddingChunkRepository chunks;
    private final Clock clock;

    public AiScopeResolver(MembershipQuery memberships, GenealogyQuery genealogy, EmbeddingChunkRepository chunks,
                           Clock clock) {
        this.memberships = memberships;
        this.genealogy = genealogy;
        this.chunks = chunks;
        this.clock = clock;
    }

    /**
     * SYSTEM_ADMIN phải truyền {@code familyId} (400 VALID_001); vai trò khác dùng gia đình của mình, truyền gia đình
     * khác thì 403 PERM_001; chưa xác minh 403 PERM_002.
     */
    public AiScope resolve(AuthenticatedUser user, UUID requestedFamilyId) {
        if (user.isSystemAdmin()) {
            if (requestedFamilyId == null) throw ApiException.invalidField("familyId", "Bắt buộc với SYSTEM_ADMIN.");
            return new AiScope(requestedFamilyId, EffectiveRole.SYSTEM_ADMIN, null);
        }
        memberships.requireVerified(user.id());
        MembershipView membership = memberships.getMembership(user.id());
        if (!membership.verified() || membership.familyId() == null) throw new ApiException(ErrorCode.PERM_002);
        UUID familyId = membership.familyId();
        if (requestedFamilyId != null && !requestedFamilyId.equals(familyId)) throw new ApiException(ErrorCode.PERM_001);
        memberships.requireFamilyAccess(user.id(), familyId);
        return new AiScope(familyId, membership.role(), membership.branchId());
    }

    /**
     * BR-AI-13, BR-COM-03: thành viên chi Y thấy chi Y và các chi tổ tiên; trưởng chi thấy thêm các chi con. Rỗng khi
     * thấy mọi chi hoặc chưa thuộc chi nào (khi đó chỉ nội dung toàn gia đình).
     */
    public List<UUID> visibleBranches(AiScope scope) {
        UUID actor = scope.actorBranchId();
        if (scope.allBranches() || actor == null) return List.of();
        UUID familyId = scope.familyId();
        return chunks.findBranchIds(familyId).stream()
                .filter(branch -> genealogy.isBranchWithin(familyId, actor, branch)
                        || (scope.role() == EffectiveRole.BRANCH_ADMIN && genealogy.isBranchWithin(familyId, branch, actor)))
                .toList();
    }

    /**
     * Hồ sơ trẻ dưới 16 tuổi cần ẩn. Ngữ cảnh gửi cho LLM bên ngoài ẩn với mọi vai trò (privacy.md mục 6);
     * kết quả chỉ hiện trong ứng dụng thì chỉ ẩn với MEMBER.
     */
    public List<UUID> hiddenMinors(AiScope scope, boolean sentToExternalLlm) {
        if (!sentToExternalLlm && scope.role() != EffectiveRole.MEMBER) return List.of();
        return genealogy.findMinorPersonIds(scope.familyId(), LocalDate.now(clock));
    }
}
