package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.RecommendationResponse;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RecommendationService {
    private static final List<String> TYPES = List.of("PERSON", "POST", "STORY", "DOCUMENT", "EVENT", "PHOTO");
    private final EmbeddingPipelineService embeddings;
    private final EmbeddingChunkRepository repository;
    private final MembershipQuery memberships;
    private final GenealogyQuery genealogy;
    private final SystemConfigQuery config;
    private final Clock clock;

    public RecommendationService(EmbeddingPipelineService embeddings, EmbeddingChunkRepository repository,
                                 MembershipQuery memberships, GenealogyQuery genealogy,
                                 SystemConfigQuery config, Clock clock) {
        this.embeddings = embeddings; this.repository = repository; this.memberships = memberships;
        this.genealogy = genealogy; this.config = config; this.clock = clock;
    }

    public RecommendationResponse recommend(AuthenticatedUser user, String type, UUID id, UUID requestedFamily,
                                            int limit) {
        if (type == null || !TYPES.contains(type) || limit < 1 || limit > 10) throw new ApiException(ErrorCode.VALID_001);
        UUID familyId, branchId = null;
        EffectiveRole role;
        if (user.isSystemAdmin()) {
            if (requestedFamily == null) throw ApiException.invalidField("familyId", "Bắt buộc với SYSTEM_ADMIN.");
            familyId = requestedFamily; role = EffectiveRole.SYSTEM_ADMIN;
        } else {
            memberships.requireVerified(user.id());
            MembershipView membership = memberships.getMembership(user.id());
            if (membership.familyId() == null) throw new ApiException(ErrorCode.PERM_002);
            familyId = membership.familyId(); branchId = membership.branchId(); role = membership.role();
            if (requestedFamily != null && !requestedFamily.equals(familyId)) throw new ApiException(ErrorCode.PERM_001);
            memberships.requireFamilyAccess(user.id(), familyId);
        }
        if (!config.getBoolean(SystemConfigQuery.AI_SEARCH_ENABLED)) throw new ApiException(ErrorCode.AI_002);
        if (!embeddings.isConfigured()) throw new ApiException(ErrorCode.AI_001);
        try {
            String context = repository.findSourceContent(familyId, type, id);
            if (context.isBlank()) throw new ApiException(ErrorCode.NOT_FOUND_001);
            boolean allBranches = role == EffectiveRole.SYSTEM_ADMIN || (role == EffectiveRole.BRANCH_ADMIN && branchId == null);
            UUID actorBranch = branchId;
            EffectiveRole actorRole = role;
            List<UUID> visible = allBranches || actorBranch == null ? List.of() : repository.findBranchIds(familyId).stream()
                    .filter(b -> genealogy.isBranchWithin(familyId, actorBranch, b)
                            || (actorRole == EffectiveRole.BRANCH_ADMIN && genealogy.isBranchWithin(familyId, b, actorBranch))).toList();
            List<UUID> minors = role == EffectiveRole.MEMBER
                    ? genealogy.findMinorPersonIds(familyId, LocalDate.now(clock)) : List.of();
            return new RecommendationResponse(repository.recommendations(familyId, allBranches, visible, minors,
                    id, embeddings.createEmbedding(context.substring(0, Math.min(context.length(), 8000))), limit));
        } catch (ApiException ex) { throw ex; }
        catch (RuntimeException ex) { throw new ApiException(ErrorCode.AI_001); }
    }
}
