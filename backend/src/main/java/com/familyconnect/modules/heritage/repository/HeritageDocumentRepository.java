package com.familyconnect.modules.heritage.repository;

import com.familyconnect.modules.heritage.entity.HeritageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HeritageDocumentRepository extends JpaRepository<HeritageDocument, UUID> {

    Page<HeritageDocument> findAllByFamilyIdAndDeletedAtIsNull(UUID familyId, Pageable pageable);

    Page<HeritageDocument> findAllByFamilyIdAndCategoryAndDeletedAtIsNull(
            UUID familyId, String category, Pageable pageable);

    Optional<HeritageDocument> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByMediaIdAndDeletedAtIsNull(UUID mediaId);

    @Query("select document.mediaId from HeritageDocument document "
            + "where document.familyId = :familyId and document.deletedAt is null")
    List<UUID> findMediaIdByFamilyIdAndDeletedAtIsNull(@Param("familyId") UUID familyId);
}
