package com.familyconnect.modules.community.repository;

import com.familyconnect.modules.community.dto.CommunityDto.PostAudience;
import com.familyconnect.modules.community.dto.CommunityDto.PostType;
import com.familyconnect.modules.community.dto.CommunityDto.ReactionType;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CommunityStore {
    record Post(UUID id, UUID familyId, UUID branchId, String content, PostType type,
                UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, PostAudience audience) {
        /** Bài chưa ghi người xem: suy ra từ branchId như trước BR-COM-13. */
        public Post(UUID id, UUID familyId, UUID branchId, String content, PostType type,
                    UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
            this(id, familyId, branchId, content, type, createdBy, createdAt, updatedAt,
                 branchId == null ? PostAudience.FAMILY : PostAudience.BRANCH);
        }
    }
    record Comment(UUID id, UUID postId, String content, UUID createdBy,
                   OffsetDateTime createdAt, OffsetDateTime updatedAt, UUID parentId) {
        public Comment(UUID id, UUID postId, String content, UUID createdBy,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {
            this(id, postId, content, createdBy, createdAt, updatedAt, null);
        }
    }
    record Reaction(UUID id, UUID postId, ReactionType type, UUID createdBy,
                    OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Page<T>(List<T> items, long total) {}

    Post insertPost(UUID family, UUID branch, String content, PostType type, UUID userId, PostAudience audience);
    Optional<Post> findPost(UUID id);
    /** Các chi đang có bài trong gia đình, để lọc phạm vi xem (BR-COM-03). */
    List<UUID> postBranchIds(UUID familyId);
    Page<Post> posts(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                     UUID branchFilter, PostType type, UUID authorFilter,
                     java.util.Set<UUID> descendantsAuthors, int page, int size, String orderBy);
    Post updatePost(UUID id, String content, UUID branch, UUID userId, PostAudience audience);
    void deletePost(UUID id);
    /** Đếm theo lô cho một trang bài, tránh mỗi bài một truy vấn. */
    Map<UUID, Long> commentCounts(List<UUID> postIds);
    /** Luôn có đủ LIKE, LOVE, HAHA, SAD (0 nếu chưa có) cho mỗi bài. */
    Map<UUID, Map<ReactionType, Long>> reactionCounts(List<UUID> postIds);
    Map<UUID, ReactionType> myReactions(List<UUID> postIds, UUID userId);
    List<WeeklyPostCount> countPostsByWeek(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                           OffsetDateTime fromInclusive, OffsetDateTime toExclusive);
    List<WeeklyActivityCount> countWeeklyActivity(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                                  OffsetDateTime fromInclusive, OffsetDateTime toExclusive);

    record WeeklyPostCount(LocalDate weekStart, long postCount) {
    }

    record WeeklyActivityCount(LocalDate weekStart, long postCount, long commentCount, long reactionCount) {
    }

    Optional<Reaction> myReaction(UUID postId, UUID userId);
    Comment insertComment(UUID postId, UUID parentId, String content, UUID userId);
    Optional<Comment> findComment(UUID id);
    Page<Comment> comments(UUID postId, int page, int size, String orderBy);
    Comment updateComment(UUID id, String content, UUID userId);
    void deleteComment(UUID id);
    void lockPost(UUID id);
    Reaction insertReaction(UUID postId, UUID userId, ReactionType type);
    Reaction updateReaction(UUID id, UUID userId, ReactionType type);
    void deleteReaction(UUID id);
}
