package com.familyconnect.modules.heritage.repository;

import com.familyconnect.modules.heritage.entity.FamilyStory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FamilyStoryRepository extends JpaRepository<FamilyStory, UUID> {

    Page<FamilyStory> findAllByFamilyIdAndDeletedAtIsNull(UUID familyId, Pageable pageable);

    Optional<FamilyStory> findByIdAndDeletedAtIsNull(UUID id);
}
