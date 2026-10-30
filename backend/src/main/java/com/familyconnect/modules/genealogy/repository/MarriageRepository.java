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

    @Query("SELECT m FROM Marriage m WHERE (m.person1Id = :personId OR m.person2Id = :personId) AND m.deletedAt IS NULL")
    List<Marriage> findAllActiveByPersonId(@Param("personId") UUID personId);

    @Query("SELECT m FROM Marriage m WHERE (m.person1Id = :personId OR m.person2Id = :personId) AND m.status = 'MARRIED' AND m.deletedAt IS NULL")
    Optional<Marriage> findActiveMarriageByPersonId(@Param("personId") UUID personId);

    @Query("SELECT m FROM Marriage m WHERE ((m.person1Id = :p1 AND m.person2Id = :p2) OR (m.person1Id = :p2 AND m.person2Id = :p1)) AND m.deletedAt IS NULL")
    Optional<Marriage> findBetweenPersons(@Param("p1") UUID p1, @Param("p2") UUID p2);
}
