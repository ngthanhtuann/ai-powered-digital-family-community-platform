package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID>, DirectoryPersonSearchRepository {

    Optional<Person> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Person> findByUserIdAndDeletedAtIsNull(UUID userId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM parent_child
                WHERE parent_id = :parentId AND child_id = :childId AND deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean isActiveParentOf(@Param("parentId") UUID parentId, @Param("childId") UUID childId);

    /** Tìm theo họ tên (không phân biệt hoa thường) và lọc chi; tham số null thì bỏ qua điều kiện đó. */
    @Query("""
            select p from Person p
            where p.familyId = :familyId and p.deletedAt is null
              and (:branchId is null or p.branchId = :branchId)
              and (:q is null or lower(p.fullName) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Person> search(@Param("familyId") UUID familyId, @Param("branchId") UUID branchId,
                        @Param("q") String q, Pageable pageable);

    long countByFamilyIdAndDeletedAtIsNull(UUID familyId);

    long countByBranchIdAndDeletedAtIsNull(UUID branchId);

    @Query(value = """
            SELECT (SELECT COUNT(*) FROM parent_child
                    WHERE (parent_id = :personId OR child_id = :personId) AND deleted_at IS NULL)
                 + (SELECT COUNT(*) FROM marriage
                    WHERE (person1_id = :personId OR person2_id = :personId) AND deleted_at IS NULL)
            """, nativeQuery = true)
    long countActiveRelations(@Param("personId") UUID personId);

    /** BR-GEN-08: người được đánh dấu đã mất thì hôn nhân MARRIED chuyển WIDOWED, end_date = ngày mất. */
    @Modifying
    @Query(value = """
            UPDATE marriage SET status = 'WIDOWED', end_date = :deathDate, updated_by = :actor, updated_at = now()
            WHERE (person1_id = :personId OR person2_id = :personId)
              AND status = 'MARRIED' AND deleted_at IS NULL
            """, nativeQuery = true)
    int widowMarriages(@Param("personId") UUID personId, @Param("deathDate") LocalDate deathDate,
                       @Param("actor") UUID actor);
}
