package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    /** Mã còn hiệu lực, mới nhất trước (BR-AUTH-24: bình thường chỉ có một). */
    @Query("""
            select c from PasswordResetCode c
            where c.userId = :userId and c.usedAt is null and c.deletedAt is null and c.expiresAt > :now
            order by c.createdAt desc
            """)
    List<PasswordResetCode> findActive(UUID userId, OffsetDateTime now);

    /** Cấp mã mới thì hủy mã cũ chưa dùng. */
    @Modifying
    @Query("""
            update PasswordResetCode c set c.deletedAt = :now, c.updatedAt = :now
            where c.userId = :userId and c.usedAt is null and c.deletedAt is null
            """)
    int discardActive(UUID userId, OffsetDateTime now);
}
