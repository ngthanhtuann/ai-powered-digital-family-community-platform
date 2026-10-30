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
                             Integer birthYear, LocalDate birthDate, String birthplace, String currentProvince,
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

    /** genealogy.yaml ParentChild. */
    public record ParentChildData(UUID parentId, UUID childId, String parentRole, String kind,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /** genealogy.yaml Marriage. */
    public record MarriageData(UUID id, UUID person1Id, UUID person2Id, String status, LocalDate startDate,
                               LocalDate endDate, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    /** genealogy.yaml TreeNode; gender, generation null với trẻ dưới 16 tuổi khi người xem là MEMBER (BR-GEN-10). */
    public record TreeNode(UUID id, String fullName, String gender, Integer birthYear, boolean isDeceased,
                           boolean hasMoreChildren, Integer generation) {
    }

    /** genealogy.yaml TreeEdge: PARENT_CHILD (source là cha/mẹ, kèm kind) hoặc MARRIAGE (kèm status). */
    public record TreeEdge(String id, UUID source, UUID target, String type, String kind, String status) {
    }

    public record TreeData(UUID rootId, List<TreeNode> nodes, List<TreeEdge> edges) {
    }
}


