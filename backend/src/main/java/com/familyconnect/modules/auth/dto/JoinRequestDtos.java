package com.familyconnect.modules.auth.dto;

import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Request và response của /join-requests (auth.yaml, UC-AUTH-06, 07). */
public final class JoinRequestDtos {

    private JoinRequestDtos() {
    }

    /** Schema JoinRequestCreateRequest: code là mã gia đình hoặc mã chi (BR-AUTH-09). */
    public record CreateRequest(@NotNull UUID code, @Size(max = 255) String message) {
    }

    public enum Decision { APPROVED, REJECTED }

    /** Schema JoinRequestReviewRequest. */
    public record ReviewRequest(@NotNull Decision status, @Size(max = 255) String rejectReason, UUID personId,
                                UUID branchId) {
    }

    /** Schema JoinRequest: email người xin chỉ hiện cho người duyệt và chính người đó (BR-AUTH-19). */
    public record JoinRequestData(UUID id, UUID userId, String applicantName, String applicantEmail, String message,
                                  UUID familyId, UUID branchId, VerificationStatus status, String rejectReason,
                                  UUID reviewedBy, OffsetDateTime reviewedAt, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt) {
        public static JoinRequestData of(MemberVerification mv, User applicant) {
            return new JoinRequestData(mv.getId(), mv.getUserId(), applicant == null ? null : applicant.getFullName(),
                    applicant == null ? null : applicant.getEmail(), mv.getMessage(), mv.getFamilyId(),
                    mv.getBranchId(), mv.getStatus(), mv.getRejectReason(), mv.getReviewedBy(), mv.getReviewedAt(),
                    mv.getCreatedAt(), mv.getUpdatedAt());
        }
    }
}
