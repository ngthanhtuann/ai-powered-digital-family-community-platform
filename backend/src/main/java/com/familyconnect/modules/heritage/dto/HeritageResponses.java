package com.familyconnect.modules.heritage.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class HeritageResponses {

    private HeritageResponses() {
    }

    public record DocumentData(UUID id, String title, String description, String category, UUID mediaId,
                               String contentUrl, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record StoryData(UUID id, String title, String content, String authorName, UUID authorId,
                            UUID relatedPersonId, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PaginationMeta(int page, int size, long total) {
    }

    public record ListResponse<T>(boolean success, List<T> data, PaginationMeta meta) {
        public static <T> ListResponse<T> of(List<T> data, int page, int size, long total) {
            return new ListResponse<>(true, data, new PaginationMeta(page, size, total));
        }
    }
}
