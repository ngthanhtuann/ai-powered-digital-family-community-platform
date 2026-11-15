package com.familyconnect.modules.auth.dto;

import com.familyconnect.modules.auth.entity.MemberRole;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Dữ liệu trả về của module auth, khớp schema trong docs/04-api/openapi/auth.yaml. */
public final class AuthResponses {

    private AuthResponses() {
    }

    /** Schema User. Không bao giờ chứa password_hash (BR-AUTH-19). */
    public record UserData(
            UUID id,
            String email,
            String fullName,
            String phone,
            LocalDate dateOfBirth,
            List<String> roles,
            UserStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static UserData of(User user, List<String> roles) {
            return new UserData(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                    user.getDateOfBirth(), roles, user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    /** Schema Membership: frontend dùng để chọn màn hình sau đăng nhập. */
    public record MembershipData(
            MembershipStatus status,
            MemberRole role,
            UUID familyId,
            String familyName,
            UUID branchId,
            String branchName,
            UUID joinRequestId,
            String rejectReason,
            boolean canCreateFamily
    ) {
        public static MembershipData none() {
            return new MembershipData(MembershipStatus.NONE, null, null, null, null, null, null, null, true);
        }
    }

    /** Schema MembershipStatus. */
    public enum MembershipStatus { NONE, PENDING, APPROVED, REJECTED, REMOVED }

    /** Schema TokenData. */
    public record TokenData(String accessToken, String refreshToken, String tokenType, long expiresIn) {
        public static TokenData bearer(String accessToken, String refreshToken, long expiresIn) {
            return new TokenData(accessToken, refreshToken, "Bearer", expiresIn);
        }
    }

    /** Schema LoginData. */
    public record LoginData(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserData user,
            MembershipData membership
    ) {
        public static LoginData of(TokenData tokens, UserData user, MembershipData membership) {
            return new LoginData(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(),
                    tokens.expiresIn(), user, membership);
        }
    }
}
