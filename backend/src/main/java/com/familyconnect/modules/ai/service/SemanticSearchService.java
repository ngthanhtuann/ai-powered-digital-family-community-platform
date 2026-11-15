package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.SemanticSearchResponse;
import com.familyconnect.modules.ai.dto.SemanticSearchResult;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.ai.service.AiScopeResolver.AiScope;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** UC-AI-01 tìm kiếm ngữ nghĩa: lọc family_id, phạm vi chi (BR-AI-13) và trẻ dưới 16 tuổi trước khi xếp hạng. */
@Service
public class SemanticSearchService {
    private final EmbeddingPipelineService embeddingPipeline;
    private final EmbeddingChunkRepository repository;
    private final AiScopeResolver scopes;
    private final AiSearchRateLimiter rateLimiter;
    private final double minimumScore;
    private final boolean enabled;
    private final SystemConfigQuery config;

    public SemanticSearchService(EmbeddingPipelineService embeddingPipeline, EmbeddingChunkRepository repository,
                                 AiScopeResolver scopes, AiSearchRateLimiter rateLimiter, SystemConfigQuery config,
                                 @Value("${ai.search.minimum-score:0.65}") double minimumScore,
                                 @Value("${ai.search.enabled:true}") boolean enabled) {
        this.embeddingPipeline = embeddingPipeline;
        this.repository = repository;
        this.scopes = scopes;
        this.rateLimiter = rateLimiter;
        this.config = config;
        this.minimumScore = minimumScore;
        this.enabled = enabled;
    }

    public SemanticSearchResponse search(AuthenticatedUser user, String query, UUID requestedFamilyId,
                                         int page, int size) {
        // Biến môi trường là công tắc lúc triển khai; cấu hình hệ thống là công tắc của quản trị (SCRUM-94).
        if (!enabled || !config.getBoolean(SystemConfigQuery.AI_SEARCH_ENABLED)) throw new ApiException(ErrorCode.AI_002);
        if (query == null || query.isBlank() || query.length() > 500 || page < 0 || size < 1 || size > 100
                || page > Integer.MAX_VALUE / size) {
            throw new ApiException(ErrorCode.VALID_001);
        }
        AiScope scope = scopes.resolve(user, requestedFamilyId);
        UUID familyId = scope.familyId();
        rateLimiter.check(user.id());
        if (!embeddingPipeline.isConfigured()) throw new ApiException(ErrorCode.AI_001);
        try {
            float[] vector = embeddingPipeline.createEmbedding(query.trim());
            boolean allBranches = scope.allBranches();
            List<UUID> branches = scopes.visibleBranches(scope);
            List<UUID> excludedPeople = scopes.hiddenMinors(scope, false);
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
}
