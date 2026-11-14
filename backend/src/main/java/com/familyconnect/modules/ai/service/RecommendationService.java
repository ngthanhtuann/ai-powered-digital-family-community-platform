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
    private final MembershipQuery memberships;
    private final GenealogyQuery genealogy;
    private final SystemConfigQuery config;
    private final Clock clock;

    public RecommendationService(EmbeddingChunkRepository repository, MembershipQuery memberships,
                                 GenealogyQuery genealogy, SystemConfigQuery config, Clock clock) {
        this.repository = repository; this.memberships = memberships;
        this.genealogy = genealogy; this.config = config; this.clock = clock;
    }

    public RecommendationResponse recommend(AuthenticatedUser user, String type, UUID id, UUID requestedFamily,
                                            int limit) {
        if (type == null || !LABELS.containsKey(type)) throw ApiException.invalidField("contextType", "Loại ngữ cảnh không hợp lệ.");
        if (limit < 1 || limit > 10) throw ApiException.invalidField("limit", "Từ 1 đến 10.");
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

        boolean allBranches = role == EffectiveRole.SYSTEM_ADMIN || (role == EffectiveRole.BRANCH_ADMIN && branchId == null);
        UUID actorBranch = branchId;
        EffectiveRole actorRole = role;
        List<UUID> visible = allBranches || actorBranch == null ? List.of() : repository.findBranchIds(familyId).stream()
                .filter(b -> genealogy.isBranchWithin(familyId, actorBranch, b)
                        || (actorRole == EffectiveRole.BRANCH_ADMIN && genealogy.isBranchWithin(familyId, b, actorBranch))).toList();
        List<UUID> minors = role == EffectiveRole.MEMBER
                ? genealogy.findMinorPersonIds(familyId, LocalDate.now(clock)) : List.of();

        String vector = repository.findVisibleSourceVector(familyId, allBranches, visible, minors, type, id);
        if (vector == null) {
            // Có trong chỉ mục nhưng ngoài phạm vi xem (gia đình/chi khác, hồ sơ trẻ em bị ẩn) → 403; không có → 404.
            throw new ApiException(repository.countActiveBySource(type, id) > 0 ? ErrorCode.PERM_001 : ErrorCode.NOT_FOUND_001);
        }
        String reason = "Nội dung gần với " + LABELS.get(type) + " đang xem.";
        return new RecommendationResponse(repository.recommendations(familyId, allBranches, visible, minors, type, id,
                        vector, limit).stream()
                .map(r -> new RecommendationResponse.Item(r.sourceType(), r.sourceId(), r.title(), reason,
                        r.snippet(), r.score(), r.path()))
                .toList());
    }
}
