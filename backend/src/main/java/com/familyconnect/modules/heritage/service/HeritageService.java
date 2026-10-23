package com.familyconnect.modules.heritage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.UserDirectory;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.community.api.CommunityQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.heritage.dto.HeritageRequests.CreateDocument;
import com.familyconnect.modules.heritage.dto.HeritageRequests.CreateStory;
import com.familyconnect.modules.heritage.dto.HeritageResponses.DocumentData;
import com.familyconnect.modules.heritage.dto.HeritageResponses.ListResponse;
import com.familyconnect.modules.heritage.dto.HeritageResponses.StoryData;
import com.familyconnect.modules.heritage.entity.FamilyStory;
import com.familyconnect.modules.heritage.entity.HeritageDocument;
import com.familyconnect.modules.heritage.repository.FamilyStoryRepository;
import com.familyconnect.modules.heritage.repository.HeritageDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class HeritageService {

    private static final Logger log = LoggerFactory.getLogger(HeritageService.class);
    static final String DELETED_AUTHOR_NAME = "Thành viên đã rời hệ thống";

    private final HeritageDocumentRepository documentRepository;
    private final FamilyStoryRepository storyRepository;
    private final MembershipQuery membershipQuery;
    private final CommunityQuery communityQuery;
    private final GenealogyQuery genealogyQuery;
    private final UserDirectory userDirectory;
    private final AiGateway aiGateway;
    private final Clock clock;

    public HeritageService(HeritageDocumentRepository documentRepository, FamilyStoryRepository storyRepository,
                           MembershipQuery membershipQuery, CommunityQuery communityQuery,
                           GenealogyQuery genealogyQuery, UserDirectory userDirectory,
                           AiGateway aiGateway, Clock clock) {
        this.documentRepository = documentRepository;
        this.storyRepository = storyRepository;
        this.membershipQuery = membershipQuery;
        this.communityQuery = communityQuery;
        this.genealogyQuery = genealogyQuery;
        this.userDirectory = userDirectory;
        this.aiGateway = aiGateway;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResponse<DocumentData> listDocuments(UUID requestedFamilyId, String category,
                                                     int page, int size, boolean ascending) {
        if (category != null && category.length() > 50) {
            throw ApiException.invalidField("category", "Danh mục tối đa 50 ký tự");
        }
        UUID familyId = resolveFamily(requestedFamilyId);
        Page<HeritageDocument> results = category == null
                ? documentRepository.findAllByFamilyIdAndDeletedAtIsNull(familyId, pageRequest(page, size, ascending))
                : documentRepository.findAllByFamilyIdAndCategoryAndDeletedAtIsNull(
                        familyId, category, pageRequest(page, size, ascending));
        return ListResponse.of(results.getContent().stream().map(this::toDocumentData).toList(),
                page, size, results.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DocumentData getDocument(UUID id) {
        HeritageDocument document = findDocument(id);
        requireFamilyAccess(document.getFamilyId());
        return toDocumentData(document);
    }

    @Transactional
    public DocumentData createDocument(UUID requestedFamilyId, CreateDocument request) {
        UUID familyId = resolveFamily(requestedFamilyId);
        requireAdmin(familyId);
        CommunityQuery.HeritageMedia media = communityQuery.getHeritageMedia(request.mediaId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (!"HERITAGE".equals(media.ownerType())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (!familyId.equals(media.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }

        UUID actorId = CurrentUser.require().id();
        HeritageDocument document = documentRepository.save(new HeritageDocument(
                familyId, request.title().trim(), request.description(), request.category(),
                request.mediaId(), actorId));
        indexDocument(document);
        return toDocumentData(document);
    }

    @Transactional
    public DocumentData updateDocument(UUID id, JsonNode patch) {
        HeritageDocument document = findDocument(id);
        requireAdmin(document.getFamilyId());
        ensurePatchFields(patch, "title", "description", "category");
        String title = text(patch, "title");
        String description = nullableText(patch, "description");
        String category = nullableText(patch, "category");
        if (patch.has("title") && (title == null || title.isBlank() || title.length() > 200)) {
            throw ApiException.invalidField("title", "Tiêu đề không được để trống và tối đa 200 ký tự");
        }
        if (patch.has("category") && category != null && category.length() > 50) {
            throw ApiException.invalidField("category", "Danh mục tối đa 50 ký tự");
        }
        if (!hasAny(patch, "title", "description", "category")) {
            throw ApiException.invalidField("body", "Cần gửi ít nhất một trường để cập nhật");
        }
        document.update(title == null ? null : title.trim(), description, patch.has("description"),
                category, patch.has("category"), CurrentUser.require().id());
        indexDocument(document);
        return toDocumentData(document);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        HeritageDocument document = findDocument(id);
        requireAdmin(document.getFamilyId());
        document.delete(OffsetDateTime.now(clock), CurrentUser.require().id());
        afterCommit(() -> aiGateway.removeSource(AiGateway.SourceType.DOCUMENT, document.getId()));
    }

    @Transactional(readOnly = true)
    public ListResponse<StoryData> listStories(UUID requestedFamilyId, int page, int size, boolean ascending) {
        UUID familyId = resolveFamily(requestedFamilyId);
        Page<FamilyStory> results = storyRepository.findAllByFamilyIdAndDeletedAtIsNull(
                familyId, pageRequest(page, size, ascending));
        Map<UUID, String> authorNames = userDirectory.getFullNames(
                results.getContent().stream().map(FamilyStory::getCreatedBy).collect(Collectors.toSet()));
        return ListResponse.of(results.getContent().stream().map(story -> toStoryData(story, authorNames)).toList(),
                page, size, results.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StoryData getStory(UUID id) {
        FamilyStory story = findStory(id);
        requireFamilyAccess(story.getFamilyId());
        return toStoryData(story);
    }

    @Transactional
    public StoryData createStory(UUID requestedFamilyId, CreateStory request) {
        UUID familyId = resolveFamily(requestedFamilyId);
        requireFamilyAccess(familyId);
        validateRelatedPerson(request.relatedPersonId(), familyId);
        UUID actorId = CurrentUser.require().id();
        FamilyStory story = storyRepository.save(new FamilyStory(
                familyId, request.title().trim(), request.content().trim(), request.relatedPersonId(), actorId));
        indexStory(story);
        return toStoryData(story);
    }

    @Transactional
    public StoryData updateStory(UUID id, JsonNode patch) {
        FamilyStory story = findStory(id);
        requireCanManageStory(story);
        ensurePatchFields(patch, "title", "content", "relatedPersonId");
        if (!hasAny(patch, "title", "content", "relatedPersonId")) {
            throw ApiException.invalidField("body", "Cần gửi ít nhất một trường để cập nhật");
        }

        String title = text(patch, "title");
        String content = text(patch, "content");
        boolean updateRelatedPerson = patch.has("relatedPersonId");
        UUID relatedPersonId = uuid(patch.get("relatedPersonId"), "relatedPersonId");
        if (patch.has("title") && (title == null || title.isBlank() || title.length() > 200)) {
            throw ApiException.invalidField("title", "Tiêu đề không được để trống và tối đa 200 ký tự");
        }
        if (patch.has("content") && (content == null || content.isBlank())) {
            throw ApiException.invalidField("content", "Nội dung không được để trống");
        }
        if (updateRelatedPerson) {
            validateRelatedPerson(relatedPersonId, story.getFamilyId());
        }
        story.update(title == null ? null : title.trim(), content == null ? null : content.trim(),
                relatedPersonId, updateRelatedPerson, CurrentUser.require().id());
        indexStory(story);
        return toStoryData(story);
    }

    @Transactional
    public void deleteStory(UUID id) {
        FamilyStory story = findStory(id);
        requireCanManageStory(story);
        story.delete(OffsetDateTime.now(clock), CurrentUser.require().id());
        afterCommit(() -> aiGateway.removeSource(AiGateway.SourceType.STORY, story.getId()));
    }

    private UUID resolveFamily(UUID requestedFamilyId) {
        AuthenticatedUser user = CurrentUser.require();
        MembershipView membership = membershipQuery.getMembership(user.id());
        if (membership.role() == EffectiveRole.SYSTEM_ADMIN) {
            if (requestedFamilyId == null) {
                throw ApiException.invalidField("familyId", "SYSTEM_ADMIN phải truyền familyId");
            }
            return requestedFamilyId;
        }
        if (!membership.verified()) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        if (membership.familyId() == null) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        if (requestedFamilyId != null && !requestedFamilyId.equals(membership.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        membershipQuery.requireFamilyAccess(user.id(), membership.familyId());
        return membership.familyId();
    }

    private void requireFamilyAccess(UUID familyId) {
        membershipQuery.requireFamilyAccess(CurrentUser.require().id(), familyId);
    }

    private void requireAdmin(UUID familyId) {
        requireFamilyAccess(familyId);
        EffectiveRole role = membershipQuery.getMembership(CurrentUser.require().id()).role();
        if (role != EffectiveRole.BRANCH_ADMIN && role != EffectiveRole.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private void requireCanManageStory(FamilyStory story) {
        requireFamilyAccess(story.getFamilyId());
        MembershipView membership = membershipQuery.getMembership(CurrentUser.require().id());
        if (membership.role() != EffectiveRole.SYSTEM_ADMIN
                && membership.role() != EffectiveRole.BRANCH_ADMIN
                && !story.getCreatedBy().equals(membership.userId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private void validateRelatedPerson(UUID personId, UUID familyId) {
        if (personId == null) {
            return;
        }
        UUID personFamilyId = genealogyQuery.findPersonFamilyId(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (!familyId.equals(personFamilyId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private HeritageDocument findDocument(UUID id) {
        return documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private FamilyStory findStory(UUID id) {
        return storyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private DocumentData toDocumentData(HeritageDocument document) {
        return new DocumentData(document.getId(), document.getTitle(), document.getDescription(),
                document.getCategory(), document.getMediaId(),
                "/api/v1/media/" + document.getMediaId() + "/content",
                document.getCreatedAt(), document.getUpdatedAt());
    }

    private StoryData toStoryData(FamilyStory story) {
        return toStoryData(story, userDirectory.getFullNames(Set.of(story.getCreatedBy())));
    }

    /** Tài khoản tác giả đã xóa thì vẫn hiện câu chuyện với tên thay thế, không làm hỏng cả danh sách. */
    private StoryData toStoryData(FamilyStory story, Map<UUID, String> authorNames) {
        String authorName = authorNames.getOrDefault(story.getCreatedBy(), DELETED_AUTHOR_NAME);
        return new StoryData(story.getId(), story.getTitle(), story.getContent(), authorName,
                story.getCreatedBy(), story.getRelatedPersonId(), story.getCreatedAt(), story.getUpdatedAt());
    }

    /** Tài liệu PDF scan không đọc được nội dung nên chỉ lập chỉ mục tiêu đề, danh mục, mô tả (ai.md, BR-AI-07). */
    private void indexDocument(HeritageDocument document) {
        String content = joinLines(document.getTitle(), document.getCategory(), document.getDescription());
        afterCommit(() -> aiGateway.upsertSource(AiGateway.SourceType.DOCUMENT, document.getId(),
                document.getFamilyId(), null, content));
    }

    private void indexStory(FamilyStory story) {
        String content = joinLines(story.getTitle(), story.getContent());
        afterCommit(() -> aiGateway.upsertSource(AiGateway.SourceType.STORY, story.getId(),
                story.getFamilyId(), null, content));
    }

    /** ai.md mục 6.1: gọi AI sau khi giao dịch commit, lỗi AI không làm hỏng thao tác của Heritage. */
    private static void afterCommit(Runnable action) {
        Runnable safeAction = () -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                log.warn("Không cập nhật được chỉ mục AI cho Heritage", ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeAction.run();
            }
        });
    }

    private static String joinLines(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                text.append(text.isEmpty() ? "" : "\n").append(part.trim());
            }
        }
        return text.toString();
    }

    private static PageRequest pageRequest(int page, int size, boolean ascending) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidField("page", "Phân trang không hợp lệ");
        }
        return PageRequest.of(page, size, Sort.by(
                ascending ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt"));
    }

    private static void ensurePatchFields(JsonNode patch, String... allowed) {
        if (patch == null || !patch.isObject()) {
            throw ApiException.invalidField("body", "Request body phải là một object");
        }
        patch.fieldNames().forEachRemaining(field -> {
            for (String valid : allowed) {
                if (valid.equals(field)) {
                    return;
                }
            }
            throw ApiException.invalidField(field, "Trường này không được cập nhật");
        });
    }

    private static boolean hasAny(JsonNode patch, String... fields) {
        for (String field : fields) {
            if (patch.has(field)) {
                return true;
            }
        }
        return false;
    }

    private static String text(JsonNode patch, String field) {
        JsonNode value = patch.get(field);
        return value == null || value.isNull() ? null : value.isTextual() ? value.textValue() : null;
    }

    private static String nullableText(JsonNode patch, String field) {
        if (!patch.has(field)) {
            return null;
        }
        JsonNode value = patch.get(field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw ApiException.invalidField(field, "Giá trị phải là chuỗi hoặc null");
        }
        return value.textValue();
    }

    private static UUID uuid(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw ApiException.invalidField(field, "Giá trị phải là UUID hoặc null");
        }
        try {
            return UUID.fromString(value.textValue());
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalidField(field, "UUID không hợp lệ");
        }
    }
}
