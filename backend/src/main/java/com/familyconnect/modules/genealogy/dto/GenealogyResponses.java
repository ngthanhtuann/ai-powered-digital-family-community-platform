package com.familyconnect.modules.genealogy.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response của genealogy.yaml: Family, Branch, Person và danh sách có meta phân trang. */
public final class GenealogyResponses {

    private GenealogyResponses() {
    }

    public record FamilyData(UUID id, String name, String description, UUID createdBy,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record BranchData(UUID id, UUID familyId, String name, UUID parentBranchId, UUID originPersonId,
                             String description, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /** Trường bị ẩn theo vai trò và tuổi trả null (BR-GEN-10). Không trả userId. */
    public record PersonData(UUID id, UUID familyId, UUID branchId, String fullName, String gender,
                             Integer birthYear, LocalDate birthDate, String birthplace, String currentProvince, String occupation,
                             String currentAddress, boolean isDeceased, LocalDate deathDate, String burialPlace,
                             UUID avatarMediaId, String note, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record PaginationMeta(int page, int size, long total) {
    }

    public record ListResponse<T>(boolean success, List<T> data, PaginationMeta meta) {
        public static <T> ListResponse<T> of(List<T> data, int page, int size, long total) {
            return new ListResponse<>(true, data, new PaginationMeta(page, size, total));
        }
    }
}
