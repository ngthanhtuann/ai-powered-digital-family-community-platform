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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Yêu cầu tham gia gia đình và tình trạng thành viên (bảng member_verification).
 * Một nguồn sự thật về gia đình của tài khoản (BR-AUTH-11). family_id, branch_id chỉ là UUID, không FK.
 */
@Entity
@Table(name = "member_verification")
public class MemberVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(length = 255)
    private String message;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

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

    protected MemberVerification() {
    }

    /** Chủ gia đình khi người dùng tự tạo gia đình (BR-AUTH-12): APPROVED, BRANCH_ADMIN, quản lý cả gia đình. */
    public static MemberVerification familyOwner(UUID userId, UUID familyId, OffsetDateTime now) {
        MemberVerification mv = new MemberVerification();
        mv.userId = userId;
        mv.familyId = familyId;
        mv.branchId = null;
        mv.role = MemberRole.BRANCH_ADMIN;
        mv.status = VerificationStatus.APPROVED;
        mv.reviewedAt = now;
        mv.createdBy = userId;
        mv.updatedBy = userId;
        return mv;
    }

    public void cancel(UUID actor) {
        this.status = VerificationStatus.CANCELLED;
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public MemberRole getRole() {
        return role;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
