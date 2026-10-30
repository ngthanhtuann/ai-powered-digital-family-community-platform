package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.ParentChild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParentChildRepository extends JpaRepository<ParentChild, UUID> {

    List<ParentChild> findByChildIdAndDeletedAtIsNull(UUID childId);

    List<ParentChild> findByParentIdAndDeletedAtIsNull(UUID parentId);

    Optional<ParentChild> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByParentIdAndChildIdAndDeletedAtIsNull(UUID parentId, UUID childId);

    long countByChildIdAndParentRoleAndKindAndDeletedAtIsNull(UUID childId, String parentRole, String kind);

    @Query(value = "WITH RECURSIVE ancestors AS (" +
            "  SELECT parent_id FROM parent_child WHERE child_id = :personId AND deleted_at IS NULL " +
            "  UNION " +
            "  SELECT pc.parent_id FROM parent_child pc JOIN ancestors a ON pc.child_id = a.parent_id WHERE pc.deleted_at IS NULL" +
            ") SELECT EXISTS (SELECT 1 FROM ancestors WHERE parent_id = :targetAncestorId)", nativeQuery = true)
    boolean isAncestorOf(@Param("personId") UUID personId, @Param("targetAncestorId") UUID targetAncestorId);
}
