package com.familyconnect.modules.community.repository;

import com.familyconnect.modules.community.dto.CommunityDto.PostType;
import com.familyconnect.modules.community.dto.CommunityDto.ReactionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CommunityStore {
    record Post(UUID id, UUID familyId, UUID branchId, String content, PostType type,
                UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Comment(UUID id, UUID postId, String content, UUID createdBy,
                   OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Reaction(UUID id, UUID postId, ReactionType type, UUID createdBy,
                    OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Page<T>(List<T> items, long total) {}

    Post insertPost(UUID family, UUID branch, String content, PostType type, UUID userId);
    Optional<Post> findPost(UUID id);
    /** Các chi đang có bài trong gia đình, để lọc phạm vi xem (BR-COM-03). */
    List<UUID> postBranchIds(UUID familyId);
    Page<Post> posts(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                     UUID branchFilter, PostType type, int page, int size, String orderBy);
    Post updatePost(UUID id, String content, UUID branch, UUID userId);
    void deletePost(UUID id);
    /** Đếm theo lô cho một trang bài, tránh mỗi bài một truy vấn. */
    Map<UUID, Long> commentCounts(List<UUID> postIds);
    /** Luôn có đủ LIKE, LOVE, HAHA, SAD (0 nếu chưa có) cho mỗi bài. */
    Map<UUID, Map<ReactionType, Long>> reactionCounts(List<UUID> postIds);
    Map<UUID, ReactionType> myReactions(List<UUID> postIds, UUID userId);
    Optional<Reaction> myReaction(UUID postId, UUID userId);
    Comment insertComment(UUID postId, String content, UUID userId);
    Optional<Comment> findComment(UUID id);
    Page<Comment> comments(UUID postId, int page, int size, String orderBy);
    Comment updateComment(UUID id, String content, UUID userId);
    void deleteComment(UUID id);
    void lockPost(UUID id);
    Reaction insertReaction(UUID postId, UUID userId, ReactionType type);
    Reaction updateReaction(UUID id, UUID userId, ReactionType type);
    void deleteReaction(UUID id);
}
