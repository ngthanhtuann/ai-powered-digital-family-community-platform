package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.Marriage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarriageRepository extends JpaRepository<Marriage, UUID> {

    Optional<Marriage> findByIdAndDeletedAtIsNull(UUID id);

    /** Hôn nhân MARRIED đang hiệu lực của một người (BR-GEN-07 chỉ cho phép một). */
    @Query("""
            select m from Marriage m
            where (m.person1Id = :personId or m.person2Id = :personId)
              and m.status = 'MARRIED' and m.deletedAt is null
            """)
    List<Marriage> findMarriedByPersonId(@Param("personId") UUID personId);
}
