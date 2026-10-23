package com.familyconnect.modules.heritage.repository;

import com.familyconnect.modules.heritage.entity.HeritageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HeritageDocumentRepository extends JpaRepository<HeritageDocument, UUID> {

    Page<HeritageDocument> findAllByFamilyIdAndDeletedAtIsNull(UUID familyId, Pageable pageable);

    Page<HeritageDocument> findAllByFamilyIdAndCategoryAndDeletedAtIsNull(
            UUID familyId, String category, Pageable pageable);

    Optional<HeritageDocument> findByIdAndDeletedAtIsNull(UUID id);
}
