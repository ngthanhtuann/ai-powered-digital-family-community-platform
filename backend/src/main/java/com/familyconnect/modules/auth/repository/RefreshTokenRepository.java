package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi đúng một token nếu chưa bị thu hồi. Trả 0 khi request khác đã dùng token này trước
     * (hai request refresh cùng lúc chỉ một cái thành công).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(UUID id, OffsetDateTime now);

    /** Thu hồi mọi refresh token còn hiệu lực của người dùng (đăng xuất, đổi mật khẩu, khóa). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(UUID userId, OffsetDateTime now);
}
