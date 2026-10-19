package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberVerificationRepository extends JpaRepository<MemberVerification, UUID> {

    /** Bản ghi đang hiệu lực (PENDING hoặc APPROVED); tối đa một bản nhờ unique index từng phần. */
    @Query("""
            select mv from MemberVerification mv
            where mv.userId = :userId and mv.deletedAt is null and mv.status in :statuses
            """)
    Optional<MemberVerification> findByUserIdAndStatusIn(UUID userId, Collection<VerificationStatus> statuses);

    /** Bản ghi gần nhất theo trạng thái (dùng để hiện lý do bị từ chối). */
    Optional<MemberVerification> findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
            UUID userId, VerificationStatus status);

    @Query("""
            select mv.userId from MemberVerification mv
            where mv.familyId = :familyId and mv.status = com.familyconnect.modules.auth.entity.VerificationStatus.APPROVED
              and mv.deletedAt is null
            """)
    List<UUID> findApprovedUserIds(UUID familyId);
}
