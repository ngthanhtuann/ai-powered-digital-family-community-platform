package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.RecommendationResponse;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.ai.service.AiScopeResolver.AiScope;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC-AI-05: gợi ý dựa trên embedding đã lưu của nguồn đang xem, không gọi Gemini
 * nên không tốn hạn mức và vẫn chạy khi chưa cấu hình khóa.
 */
@Service
public class RecommendationService {
    private static final Map<String, String> LABELS = Map.of("PERSON", "hồ sơ", "POST", "bài đăng",
            "STORY", "câu chuyện", "DOCUMENT", "tài liệu", "EVENT", "sự kiện", "PHOTO", "ảnh");
    private final EmbeddingChunkRepository repository;
    private final AiScopeResolver scopes;
    private final SystemConfigQuery config;

    public RecommendationService(EmbeddingChunkRepository repository, AiScopeResolver scopes,
                                 SystemConfigQuery config) {
        this.repository = repository;
        this.scopes = scopes;
        this.config = config;
    }

    public RecommendationResponse recommend(AuthenticatedUser user, String type, UUID id, UUID requestedFamily,
                                            int limit) {
        if (type == null || !LABELS.containsKey(type)) throw ApiException.invalidField("contextType", "Loại ngữ cảnh không hợp lệ.");
        if (limit < 1 || limit > 10) throw ApiException.invalidField("limit", "Từ 1 đến 10.");
        AiScope scope = scopes.resolve(user, requestedFamily);
        UUID familyId = scope.familyId();
        if (!config.getBoolean(SystemConfigQuery.AI_SEARCH_ENABLED)) throw new ApiException(ErrorCode.AI_002);

        boolean allBranches = scope.allBranches();
        List<UUID> visible = scopes.visibleBranches(scope);
        List<UUID> minors = scopes.hiddenMinors(scope, false);

        String vector = repository.findVisibleSourceVector(familyId, allBranches, visible, minors, type, id);
        // Không có, chưa có chỉ mục hay ngoài phạm vi xem (gia đình/chi khác, trẻ em bị ẩn) đều 404: không lộ nguồn có tồn tại,
        // và trang hồ sơ (xem được mọi chi) không bị thông báo PERM_001 khi khối gợi ý tự tải.
        if (vector == null) throw new ApiException(ErrorCode.NOT_FOUND_001);
        String reason = "Nội dung gần với " + LABELS.get(type) + " đang xem.";
        return new RecommendationResponse(repository.recommendations(familyId, allBranches, visible, minors, type, id,
                        vector, limit).stream()
                .map(r -> new RecommendationResponse.Item(r.sourceType(), r.sourceId(), r.title(), reason,
                        r.snippet(), r.score(), r.path()))
                .toList());
    }
}
