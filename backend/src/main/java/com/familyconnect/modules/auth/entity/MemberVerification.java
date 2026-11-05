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

    /** Yêu cầu tham gia (UC-AUTH-06, BR-AUTH-09): PENDING, vai trò MEMBER. */
    public static MemberVerification joinRequest(UUID userId, UUID familyId, UUID branchId, String message) {
        MemberVerification mv = new MemberVerification();
        mv.userId = userId;
        mv.familyId = familyId;
        mv.branchId = branchId;
        mv.message = message;
        mv.createdBy = userId;
        mv.updatedBy = userId;
        return mv;
    }

    /** UC-AUTH-07: duyệt; người xin là MEMBER đã xác minh từ request kế tiếp (BR-AUTH-11). */
    public void approve(UUID reviewer, OffsetDateTime now) {
        review(VerificationStatus.APPROVED, null, reviewer, now);
    }

    public void reject(String reason, UUID reviewer, OffsetDateTime now) {
        review(VerificationStatus.REJECTED, reason, reviewer, now);
    }

    private void review(VerificationStatus status, String reason, UUID reviewer, OffsetDateTime now) {
        this.status = status;
        this.rejectReason = reason;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.updatedBy = reviewer;
    }

    /** Gán bởi quản trị (BR-AUTH-14): APPROVED ngay. */
    public static MemberVerification assigned(UUID userId, UUID familyId, UUID branchId, MemberRole role, UUID actor,
                                              OffsetDateTime now) {
        MemberVerification mv = new MemberVerification();
        mv.userId = userId;
        mv.familyId = familyId;
        mv.branchId = branchId;
        mv.role = role;
        mv.status = VerificationStatus.APPROVED;
        mv.reviewedBy = actor;
        mv.reviewedAt = now;
        mv.createdBy = actor;
        mv.updatedBy = actor;
        return mv;
    }

    /** Đổi vai trò, chi trong cùng gia đình (BR-AUTH-14, BR-AUTH-22). */
    public void changeRole(MemberRole role, UUID branchId, UUID actor) {
        this.role = role;
        this.branchId = branchId;
        this.updatedBy = actor;
    }

    /** Chuyển sang gia đình khác do quản trị (BR-AUTH-14): giữ bản ghi làm lịch sử. */
    public void remove(UUID actor) {
        this.status = VerificationStatus.REMOVED;
        this.updatedBy = actor;
    }

    public boolean isFamilyHead() {
        return status == VerificationStatus.APPROVED && role == MemberRole.BRANCH_ADMIN && branchId == null;
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

    public String getMessage() {
        return message;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
