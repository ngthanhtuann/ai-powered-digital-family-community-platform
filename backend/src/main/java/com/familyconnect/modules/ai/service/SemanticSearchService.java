package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.SemanticSearchResponse;
import com.familyconnect.modules.ai.dto.SemanticSearchResult;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** UC-AI-01 tìm kiếm ngữ nghĩa: lọc family_id, phạm vi chi (BR-AI-13) và trẻ dưới 16 tuổi trước khi xếp hạng. */
@Service
public class SemanticSearchService {
    private final EmbeddingPipelineService embeddingPipeline;
    private final EmbeddingChunkRepository repository;
    private final MembershipQuery membershipQuery;
    private final AiSearchRateLimiter rateLimiter;
    private final GenealogyQuery genealogyQuery;
    private final Clock clock;
    private final double minimumScore;
    private final boolean enabled;

    public SemanticSearchService(EmbeddingPipelineService embeddingPipeline, EmbeddingChunkRepository repository,
                                 MembershipQuery membershipQuery, AiSearchRateLimiter rateLimiter,
                                 GenealogyQuery genealogyQuery, Clock clock,
                                 @Value("${ai.search.minimum-score:0.88}") double minimumScore,
                                 @Value("${ai.search.enabled:true}") boolean enabled) {
        this.embeddingPipeline = embeddingPipeline;
        this.repository = repository;
        this.membershipQuery = membershipQuery;
        this.rateLimiter = rateLimiter;
        this.genealogyQuery = genealogyQuery;
        this.clock = clock;
        this.minimumScore = minimumScore;
        this.enabled = enabled;
    }

    public SemanticSearchResponse search(AuthenticatedUser user, String query, UUID requestedFamilyId,
                                         int page, int size) {
        if (!enabled) throw new ApiException(ErrorCode.AI_002);
        if (query == null || query.isBlank() || query.length() > 500 || page < 0 || size < 1 || size > 100
                || page > Integer.MAX_VALUE / size) {
            throw new ApiException(ErrorCode.VALID_001);
        }
        UUID familyId;
        UUID actorBranchId = null;
        EffectiveRole role;
        if (user.isSystemAdmin()) {
            if (requestedFamilyId == null) throw ApiException.invalidField("familyId", "Bắt buộc với SYSTEM_ADMIN.");
            familyId = requestedFamilyId;
            role = EffectiveRole.SYSTEM_ADMIN;
        } else {
            membershipQuery.requireVerified(user.id());
            MembershipView membership = membershipQuery.getMembership(user.id());
            if (membership.familyId() == null) throw new ApiException(ErrorCode.PERM_002);
            familyId = membership.familyId();
            if (requestedFamilyId != null && !requestedFamilyId.equals(familyId)) {
                throw new ApiException(ErrorCode.PERM_001);
            }
            membershipQuery.requireFamilyAccess(user.id(), familyId);
            role = membership.role();
            actorBranchId = membership.branchId();
        }
        rateLimiter.check(user.id());
        if (!embeddingPipeline.isConfigured()) throw new ApiException(ErrorCode.AI_001);
        try {
            float[] vector = embeddingPipeline.createEmbedding(query.trim());
            boolean allBranches = role == EffectiveRole.SYSTEM_ADMIN
                    || (role == EffectiveRole.BRANCH_ADMIN && actorBranchId == null);
            List<UUID> branches = allBranches ? List.of() : visibleBranches(familyId, actorBranchId, role);
            List<UUID> excludedPeople = role == EffectiveRole.MEMBER
                    ? genealogyQuery.findMinorPersonIds(familyId, LocalDate.now(clock)) : List.of();
            List<SemanticSearchResult> results = repository.search(
                    familyId, allBranches, branches, vector, minimumScore, excludedPeople, page, size);
            long total = repository.countSearch(familyId, allBranches, branches, excludedPeople, vector, minimumScore);
            return new SemanticSearchResponse(query.trim(), results, page, size, total,
                    results.isEmpty() ? "Không tìm thấy thông tin phù hợp." : null);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.AI_001);
        }
    }

    /**
     * BR-AI-13, BR-COM-03 (cùng quy tắc với Community, Events): thành viên chi Y thấy nội dung của Y và các chi tổ tiên;
     * trưởng chi còn thấy các chi con mình quản lý. Người chưa thuộc chi nào chỉ thấy nội dung toàn gia đình.
     */
    private List<UUID> visibleBranches(UUID familyId, UUID actorBranchId, EffectiveRole role) {
        if (actorBranchId == null) return List.of();
        return repository.findBranchIds(familyId).stream()
                .filter(branchId -> genealogyQuery.isBranchWithin(familyId, actorBranchId, branchId)
                        || (role == EffectiveRole.BRANCH_ADMIN
                        && genealogyQuery.isBranchWithin(familyId, branchId, actorBranchId)))
                .toList();
    }
}
