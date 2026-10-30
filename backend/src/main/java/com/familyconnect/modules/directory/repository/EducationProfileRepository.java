package com.familyconnect.modules.directory.repository;

import com.familyconnect.modules.directory.entity.EducationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EducationProfileRepository extends JpaRepository<EducationProfile, UUID> {

    Optional<EducationProfile> findByPersonIdAndDeletedAtIsNull(UUID personId);
}
