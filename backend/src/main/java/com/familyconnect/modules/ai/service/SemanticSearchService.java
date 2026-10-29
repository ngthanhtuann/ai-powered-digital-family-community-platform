package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.ai.dto.SemanticSearchResponse;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.ai.api.PersonVisibilityQuery;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.common.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class SemanticSearchService {
    private final EmbeddingPipelineService embeddingPipeline;
    private final EmbeddingChunkRepository repository;
    private final MembershipQuery membershipQuery;
    private final AiSearchRateLimiter rateLimiter;
    private final PersonVisibilityQuery personVisibilityQuery;
    private final Clock clock;
    private final double minimumScore;
    private final boolean enabled;

    public SemanticSearchService(EmbeddingPipelineService embeddingPipeline, EmbeddingChunkRepository repository,
                                 MembershipQuery membershipQuery, AiSearchRateLimiter rateLimiter,
                                 PersonVisibilityQuery personVisibilityQuery, Clock clock,
                                 @Value("${ai.search.minimum-score:0.88}") double minimumScore,
                                 @Value("${ai.search.enabled:true}") boolean enabled) {
        this.embeddingPipeline = embeddingPipeline;
        this.repository = repository;
        this.membershipQuery = membershipQuery;
        this.rateLimiter = rateLimiter;
        this.personVisibilityQuery = personVisibilityQuery;
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
        UUID branchId = null;
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
            if (role == EffectiveRole.BRANCH_ADMIN) branchId = membership.branchId();
        }
        rateLimiter.check(user.id());
        if (!embeddingPipeline.isConfigured()) throw new ApiException(ErrorCode.AI_001);
        try {
            float[] vector = embeddingPipeline.createEmbedding(query.trim());
            boolean hideMinors = role == EffectiveRole.MEMBER;
            List<UUID> excludedPeople = hideMinors
                    ? personVisibilityQuery.findMinorPersonIds(familyId, LocalDate.now(clock)) : List.of();
            List<com.familyconnect.modules.ai.dto.SemanticSearchResult> results = repository.search(
                    familyId, branchId, vector, minimumScore, excludedPeople, page, size);
            long total = repository.countSearch(familyId, branchId, excludedPeople, vector, minimumScore);
            return new SemanticSearchResponse(query.trim(), results, page, size, total,
                    results.isEmpty() ? "Không tìm thấy thông tin phù hợp." : null);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.AI_001);
        }
    }
}
