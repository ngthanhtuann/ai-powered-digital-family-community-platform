package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.Person;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface PersonRepository extends JpaRepository<Person, UUID>, DirectoryPersonSearchRepository {

    Optional<Person> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Person> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<Person> findAllByUserIdInAndDeletedAtIsNull(Collection<UUID> userIds);

    /** BR-COM-13: tài khoản của tổ tiên của mình và của vợ/chồng mình (giới hạn 30 đời để chặn dữ liệu vòng). */
    @org.springframework.data.jpa.repository.Query(value = """
            WITH RECURSIVE me(id) AS (
                SELECT id FROM person WHERE user_id = :userId AND deleted_at IS NULL
                UNION
                SELECT CASE WHEN m.person1_id = p.id THEN m.person2_id ELSE m.person1_id END
                FROM person p JOIN marriage m ON (m.person1_id = p.id OR m.person2_id = p.id)
                WHERE p.user_id = :userId AND p.deleted_at IS NULL
                  AND m.deleted_at IS NULL AND m.status <> 'DIVORCED'
            ), up(id, depth) AS (
                SELECT pc.parent_id, 1 FROM parent_child pc JOIN me ON pc.child_id = me.id
                WHERE pc.deleted_at IS NULL
                UNION
                SELECT pc.parent_id, up.depth + 1 FROM parent_child pc JOIN up ON pc.child_id = up.id
                WHERE pc.deleted_at IS NULL AND up.depth < 30
            )
            SELECT DISTINCT p.user_id FROM up JOIN person p ON p.id = up.id
            WHERE p.user_id IS NOT NULL AND p.deleted_at IS NULL
            """, nativeQuery = true)
    List<UUID> findAncestorAccountIds(@org.springframework.data.repository.query.Param("userId") UUID userId);

    /** Cả gia đình trong một truy vấn; dùng khi tính quan hệ cho nhiều người (xuất CSV, UC-DSH-02). */
    List<Person> findAllByFamilyIdAndDeletedAtIsNull(UUID familyId);

    List<Person> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    /** Cùng quy tắc isChild của PersonService: người còn sống luôn có birth_date (BR-GEN-17). */
    @Query(value = """
            SELECT id FROM person
            WHERE family_id = :familyId AND deleted_at IS NULL AND NOT is_deceased
              AND birth_date IS NOT NULL AND birth_date + INTERVAL '16 years' > :asOfDate
            """, nativeQuery = true)
    List<UUID> findMinorIds(@Param("familyId") UUID familyId, @Param("asOfDate") LocalDate asOfDate);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM parent_child
                WHERE parent_id = :parentId AND child_id = :childId AND deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean isActiveParentOf(@Param("parentId") UUID parentId, @Param("childId") UUID childId);

    /**
     * Tìm theo họ tên không phân biệt hoa thường và không dấu (unaccent, V12; như Danh bạ), lọc chi; tham số null
     * thì bỏ qua điều kiện đó. pattern là mẫu LIKE đã escape bằng "!" (SCRUM-102).
     */
    @Query("""
            select p from Person p
            where p.familyId = :familyId and p.deletedAt is null
              and (:branchId is null or p.branchId = :branchId)
              and (:pattern is null or lower(function('unaccent', p.fullName))
                   like lower(function('unaccent', cast(:pattern as string))) escape '!')
            """)
    Page<Person> search(@Param("familyId") UUID familyId, @Param("branchId") UUID branchId,
                        @Param("pattern") String pattern, Pageable pageable);

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

    /**
     * BR-GEN-21 báo nhầm: hôn nhân đã chuyển WIDOWED vì lần báo mất đó (end_date = ngày mất) quay lại MARRIED,
     * trừ khi người kia cũng đã mất hoặc đã có hôn nhân MARRIED khác.
     */
    @Modifying
    @Query(value = """
            UPDATE marriage m SET status = 'MARRIED', end_date = NULL, updated_by = :actor, updated_at = now()
            WHERE (m.person1_id = :personId OR m.person2_id = :personId)
              AND m.status = 'WIDOWED' AND m.end_date = :deathDate AND m.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM person o
                  WHERE o.id = CASE WHEN m.person1_id = :personId THEN m.person2_id ELSE m.person1_id END
                    AND o.is_deceased)
              AND NOT EXISTS (SELECT 1 FROM marriage x
                  WHERE x.id <> m.id AND x.status = 'MARRIED' AND x.deleted_at IS NULL
                    AND (x.person1_id IN (m.person1_id, m.person2_id) OR x.person2_id IN (m.person1_id, m.person2_id)))
            """, nativeQuery = true)
    int reviveMarriages(@Param("personId") UUID personId, @Param("deathDate") LocalDate deathDate,
                        @Param("actor") UUID actor);

    /** BR-GEN-24: tài khoản userId gắn với một con ruột (BIOLOGICAL) của parentId. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM parent_child pc JOIN person c ON c.id = pc.child_id
                WHERE pc.parent_id = :parentId AND pc.kind = 'BIOLOGICAL' AND pc.deleted_at IS NULL
                  AND c.deleted_at IS NULL AND c.user_id = :userId)
            """, nativeQuery = true)
    boolean isBiologicalChildAccount(@Param("parentId") UUID parentId, @Param("userId") UUID userId);

    /** BR-GEN-24: người quản lý đánh dấu mất trực tiếp thì báo mất đang chờ chuyển CONFIRMED. */
    @Modifying
    @Query(value = """
            UPDATE death_report SET status = 'CONFIRMED', confirmed_by = :actor, confirmed_at = now(),
                   updated_by = :actor, updated_at = now()
            WHERE person_id = :personId AND status = 'PENDING' AND deleted_at IS NULL
            """, nativeQuery = true)
    int confirmPendingDeathReports(@Param("personId") UUID personId, @Param("actor") UUID actor);
}
