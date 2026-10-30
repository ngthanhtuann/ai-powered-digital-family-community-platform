package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.ParentChild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentChildRepository extends JpaRepository<ParentChild, UUID> {

    Optional<ParentChild> findByParentIdAndChildIdAndDeletedAtIsNull(UUID parentId, UUID childId);

    boolean existsByParentIdAndChildIdAndDeletedAtIsNull(UUID parentId, UUID childId);

    long countByChildIdAndParentRoleAndKindAndDeletedAtIsNull(UUID childId, String parentRole, String kind);

    /** {@code ancestorId} có nằm trên chuỗi tổ tiên của {@code personId} không (BR-GEN-03); giới hạn độ sâu chống dữ liệu lỗi. */
    @Query(value = """
            WITH RECURSIVE ancestors(id, depth) AS (
                SELECT parent_id, 1 FROM parent_child WHERE child_id = :personId AND deleted_at IS NULL
                UNION
                SELECT pc.parent_id, a.depth + 1 FROM parent_child pc
                JOIN ancestors a ON pc.child_id = a.id
                WHERE pc.deleted_at IS NULL AND a.depth < 100
            )
            SELECT EXISTS (SELECT 1 FROM ancestors WHERE id = :ancestorId)
            """, nativeQuery = true)
    boolean isAncestorOf(@Param("personId") UUID personId, @Param("ancestorId") UUID ancestorId);

    /** Anh chị em ruột: có chung ít nhất một cha hoặc mẹ ruột (BR-GEN-07). */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM parent_child a
                JOIN parent_child b ON a.parent_id = b.parent_id
                WHERE a.child_id = :person1Id AND b.child_id = :person2Id
                  AND a.kind = 'BIOLOGICAL' AND b.kind = 'BIOLOGICAL'
                  AND a.deleted_at IS NULL AND b.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean areSiblings(@Param("person1Id") UUID person1Id, @Param("person2Id") UUID person2Id);
}
