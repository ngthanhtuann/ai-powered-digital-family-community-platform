package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndFamilyIdAndDeletedAtIsNull(UUID id, UUID familyId);

    /** Đi ngược chuỗi chi cha từ branchId; giới hạn độ sâu để dữ liệu lỗi có vòng lặp không treo truy vấn. */
    @Query(value = """
            WITH RECURSIVE up AS (
                SELECT id, parent_branch_id, 0 AS depth FROM branch
                WHERE id = :branchId AND family_id = :familyId AND deleted_at IS NULL
                UNION ALL
                SELECT b.id, b.parent_branch_id, up.depth + 1 FROM branch b
                JOIN up ON b.id = up.parent_branch_id
                WHERE b.family_id = :familyId AND b.deleted_at IS NULL AND up.depth < 100
            )
            SELECT EXISTS (SELECT 1 FROM up WHERE id = :ancestorBranchId)
            """, nativeQuery = true)
    boolean isWithin(@Param("familyId") UUID familyId, @Param("branchId") UUID branchId,
                     @Param("ancestorBranchId") UUID ancestorBranchId);
}
