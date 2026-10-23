package com.familyconnect.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tài khoản và hồ sơ cá nhân (bảng users, auth.md mục 5.2).
 * DynamicUpdate: chỉ ghi cột thay đổi, để việc ghi last_login_at khi đăng nhập không đè
 * trạng thái LOCKED do quản trị vừa đặt ở giao dịch khác.
 */
@Entity
@DynamicUpdate
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "lock_reason", length = 255)
    private String lockReason;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, String phone, LocalDate dateOfBirth) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedBy = this.id;
    }

    public void recordLogin(OffsetDateTime at) {
        this.lastLoginAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public UserStatus getStatus() {
        return status;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
