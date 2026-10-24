package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.Family;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface FamilyRepository extends JpaRepository<Family, UUID> {

    Optional<Family> findByIdAndDeletedAtIsNull(UUID id);

    Page<Family> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Family> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids, Pageable pageable);
}
