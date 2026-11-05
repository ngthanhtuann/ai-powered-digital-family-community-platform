package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email đã chuẩn hóa (trim, chữ thường); bỏ qua tài khoản đã xóa mềm. */
    @Query("select u from User u where u.email = :email and u.deletedAt is null")
    Optional<User> findActiveByEmail(String email);

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(UUID id);

    @Query("select u from User u where u.id in :ids and u.deletedAt is null")
    List<User> findActiveByIdIn(Collection<UUID> ids);

    @Query("select count(u) > 0 from User u where u.email = :email and u.deletedAt is null")
    boolean existsActiveByEmail(String email);

    /** Chỉ lấy trạng thái, dùng ở bộ lọc JWT mỗi request. */
    @Query("select u.status from User u where u.id = :id and u.deletedAt is null")
    Optional<UserStatus> findStatusById(UUID id);

    /**
     * Id quản trị đang hoạt động, khóa các dòng users (thứ tự cố định để không deadlock). Request khóa hoặc thu quyền
     * cùng lúc phải chờ nhau rồi đọc lại trạng thái mới, nên luôn còn ít nhất một quản trị (BR-AUTH-13).
     */
    @Query(value = """
            SELECT u.id FROM users u
            WHERE u.deleted_at IS NULL AND u.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
                          WHERE ur.user_id = u.id AND ur.deleted_at IS NULL AND r.code = 'SYSTEM_ADMIN')
            ORDER BY u.id FOR UPDATE OF u
            """, nativeQuery = true)
    List<UUID> lockActiveAdminIds();

    /**
     * Danh sách cho quản trị (UC-AUTH-08). pattern là chuỗi LIKE đã chữ thường ("%" khi không tìm); họ tên so không
     * dấu (unaccent, V12). Dùng cờ any* thay cho tham số null để PostgreSQL không lỗi suy kiểu tham số.
     */
    @Query("""
            select u from User u
            where u.deletedAt is null
              and (lower(function('unaccent', u.fullName)) like lower(function('unaccent', :pattern)) escape '!'
                   or lower(u.email) like :pattern escape '!')
              and (:anyStatus = true or u.status = :status)
              and (:anyRole = true or exists (select 1 from UserRole ur
                   where ur.userId = u.id and ur.role.code = :role and ur.deletedAt is null))
            """)
    Page<User> search(String pattern, boolean anyStatus, UserStatus status, boolean anyRole, String role,
                      Pageable pageable);
}
