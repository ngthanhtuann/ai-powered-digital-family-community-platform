package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** Trưởng họ đang có của gia đình, khóa dòng để hai người thu quyền nhau cùng lúc không làm mất hết (BR-AUTH-22). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select mv from MemberVerification mv
            where mv.familyId = :familyId and mv.status = com.familyconnect.modules.auth.entity.VerificationStatus.APPROVED
              and mv.role = com.familyconnect.modules.auth.entity.MemberRole.BRANCH_ADMIN and mv.branchId is null
              and mv.deletedAt is null
            """)
    List<MemberVerification> lockFamilyHeads(UUID familyId);

    /** Tài khoản đã xác minh của gia đình, theo họ tên (UC-AUTH-14). */
    @Query(value = """
            select mv from MemberVerification mv, User u
            where u.id = mv.userId and u.deletedAt is null and mv.familyId = :familyId and mv.deletedAt is null
              and mv.status = com.familyconnect.modules.auth.entity.VerificationStatus.APPROVED
            order by u.fullName, u.id
            """, countQuery = """
            select count(mv) from MemberVerification mv, User u
            where u.id = mv.userId and u.deletedAt is null and mv.familyId = :familyId and mv.deletedAt is null
              and mv.status = com.familyconnect.modules.auth.entity.VerificationStatus.APPROVED
            """)
    Page<MemberVerification> findApprovedInFamily(UUID familyId, Pageable pageable);
}
