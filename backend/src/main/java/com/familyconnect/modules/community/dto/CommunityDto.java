package com.familyconnect.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** JSON fields match community-events.yaml. */
public final class CommunityDto {
    private CommunityDto() {}

    public enum PostType { POST, NEWS }
    public enum ReactionType { LIKE, LOVE, HAHA, SAD }
    public record CreatePost(@NotBlank @Size(max=5000) String content, @NotNull PostType type, UUID branchId) {}
    /** Distinguish omitted branchId from explicit null (whole-family scope). */
    public static final class UpdatePost {
        @Size(max=5000)
        private String content;
        private UUID branchId;
        private PostType type;
        private boolean branchSpecified;

        public UpdatePost() {}
        public UpdatePost(String content, UUID branchId, PostType type) {
            this.content = content;
            this.branchId = branchId;
            this.type = type;
            this.branchSpecified = branchId != null;
        }
        public String content() { return content; }
        public UUID branchId() { return branchId; }
        public PostType type() { return type; }
        public boolean branchSpecified() { return branchSpecified; }
        @JsonSetter("content") public void setContent(String value) { content = value; }
        @JsonSetter("branchId") public void setBranchId(UUID value) {
            branchId = value;
            branchSpecified = true;
        }
        @JsonSetter("type") public void setType(PostType value) { type = value; }
    }
    public record CommentInput(@NotBlank @Size(max=1000) String content) {}
    public record ReactionInput(@NotNull ReactionType type) {}

    public record PostView(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                           UUID familyId, UUID branchId, String content, PostType type,
                           UUID createdBy, String authorName, boolean canDelete,
                           long commentCount, Map<ReactionType, Long> reactionCounts,
                           ReactionType myReaction, long mediaCount,
                           List<com.familyconnect.modules.community.media.MediaService.MediaView> media) {}
    public record CommentView(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                              UUID postId, String content, UUID createdBy, String authorName,
                              boolean canDelete) {}
    public record ReactionView(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               UUID postId, ReactionType type, UUID createdBy) {}
    public record Meta(int page, int size, long total) {}
    public record PageSuccess<T>(boolean success, List<T> data, Meta meta) {
        public static <T> PageSuccess<T> of(List<T> data, int page, int size, long total) {
            return new PageSuccess<>(true, data, new Meta(page, size, total));
        }
    }
}
