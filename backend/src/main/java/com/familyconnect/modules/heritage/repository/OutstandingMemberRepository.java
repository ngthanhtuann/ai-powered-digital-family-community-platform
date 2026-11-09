package com.familyconnect.modules.heritage.repository;

import com.familyconnect.modules.heritage.entity.OutstandingMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OutstandingMemberRepository extends JpaRepository<OutstandingMember, UUID> {

    Page<OutstandingMember> findAllByFamilyIdAndDeletedAtIsNull(UUID familyId, Pageable pageable);

    java.util.List<OutstandingMember> findAllByFamilyIdAndDeletedAtIsNull(UUID familyId, Sort sort);

    Optional<OutstandingMember> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByFamilyIdAndPersonIdAndDeletedAtIsNull(UUID familyId, UUID personId);
}
