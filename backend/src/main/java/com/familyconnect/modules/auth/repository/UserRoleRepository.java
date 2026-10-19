package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /** Mã vai trò toàn hệ thống còn hiệu lực của người dùng, ví dụ [MEMBER, SYSTEM_ADMIN]. */
    @Query("select ur.role.code from UserRole ur where ur.userId = :userId and ur.deletedAt is null order by ur.role.code")
    List<String> findRoleCodes(UUID userId);

    @Query("select count(ur) > 0 from UserRole ur where ur.role.code = :code and ur.deletedAt is null")
    boolean existsByRoleCode(String code);
}
