package com.familyconnect.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Mã đặt lại mật khẩu dùng một lần (UC-AUTH-15, BR-AUTH-24): chỉ lưu BCrypt của mã. */
@Entity
@Table(name = "password_reset_code")
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(UUID userId, String codeHash, OffsetDateTime expiresAt, UUID createdBy) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    /** Sai một lần; đủ maxAttempts thì hủy mã. */
    public void recordFailure(OffsetDateTime now, int maxAttempts) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            deletedAt = now;
        }
    }

    public void markUsed(OffsetDateTime now) {
        usedAt = now;
    }

    public UUID getId() { return id; }
    public String getCodeHash() { return codeHash; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
}
