package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserStatus;
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
}
