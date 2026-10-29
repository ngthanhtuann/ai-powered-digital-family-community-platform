package com.familyconnect.modules.directory.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DirectoryResponses {

    private DirectoryResponses() {
    }

    public record ProfessionProfileData(UUID id, UUID personId, String jobTitle, String company,
                                        String workLocation, OffsetDateTime createdAt,
                                        OffsetDateTime updatedAt) {
    }

    public record EducationProfileData(UUID id, UUID personId, String school, String degree,
                                       Integer graduationYear, OffsetDateTime createdAt,
                                       OffsetDateTime updatedAt) {
    }

    public record PersonSearchResult(UUID id, String fullName, String relationship, Integer birthYear,
                                     Integer generation, String jobTitle, String location) {
    }

    public record ListResponse<T>(boolean success, List<T> data, PaginationMeta meta) {
        public static <T> ListResponse<T> of(List<T> data, int page, int size, long total) {
            return new ListResponse<>(true, data, new PaginationMeta(page, size, total));
        }
    }

    public record PaginationMeta(int page, int size, long total) {
    }

    public record UpsertResult<T>(T data, boolean created) {
    }
}
