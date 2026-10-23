package com.familyconnect.modules.auth.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.auth.api.MembershipCommand;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipStatus;
import com.familyconnect.modules.auth.entity.MemberRole;
import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import com.familyconnect.modules.auth.repository.MemberVerificationRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tư cách thành viên gia đình dựa trên member_verification (auth.md mục 4.1, 6.3; BR-AUTH-08, 11, 12).
 * API xin tham gia và duyệt làm ở SCRUM-57.
 */
@Service
public class MembershipService implements MembershipQuery, MembershipCommand {

    private static final Set<VerificationStatus> ACTIVE_STATUSES =
            EnumSet.of(VerificationStatus.PENDING, VerificationStatus.APPROVED);

    private final MemberVerificationRepository verificationRepository;
    private final UserRoleRepository userRoleRepository;
    private final ObjectProvider<GenealogyPort> genealogyPort;
    private final Clock clock;

    public MembershipService(MemberVerificationRepository verificationRepository,
                             UserRoleRepository userRoleRepository,
                             ObjectProvider<GenealogyPort> genealogyPort,
                             Clock clock) {
        this.verificationRepository = verificationRepository;
        this.userRoleRepository = userRoleRepository;
        this.genealogyPort = genealogyPort;
        this.clock = clock;
    }

    // ---- MembershipQuery ----

    @Override
    @Transactional(readOnly = true)
    public MembershipView getMembership(UUID userId) {
        if (isSystemAdmin(userId)) {
            return new MembershipView(userId, null, null, EffectiveRole.SYSTEM_ADMIN, true);
        }
        return findApproved(userId)
                .map(mv -> new MembershipView(userId, mv.getFamilyId(), mv.getBranchId(),
                        mv.getRole() == MemberRole.BRANCH_ADMIN ? EffectiveRole.BRANCH_ADMIN : EffectiveRole.MEMBER,
                        true))
                .orElseGet(() -> new MembershipView(userId, null, null, EffectiveRole.MEMBER, false));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireVerified(UUID userId) {
        if (!getMembership(userId).verified()) {
            throw new ApiException(ErrorCode.PERM_002);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireFamilyAccess(UUID userId, UUID familyId) {
        MembershipView view = getMembership(userId);
        if (view.role() == EffectiveRole.SYSTEM_ADMIN) {
            return;
        }
        if (!view.verified()) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        if (!Objects.equals(view.familyId(), familyId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findVerifiedUserIds(UUID familyId) {
        return verificationRepository.findApprovedUserIds(familyId);
    }

    // ---- MembershipCommand ----

    @Override
    @Transactional
    public void assignFamilyOwner(UUID userId, UUID familyId) {
        if (isSystemAdmin(userId)) {
            // SYSTEM_ADMIN không thuộc gia đình nào (BR-AUTH-08); tạo gia đình hộ thì không gọi hàm này.
            throw new ApiException(ErrorCode.PERM_001);
        }
        Optional<MemberVerification> active = verificationRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            MemberVerification current = active.get();
            if (current.getStatus() == VerificationStatus.APPROVED) {
                throw new ApiException(ErrorCode.AUTH_005);
            }
            current.cancel(userId);
            // Ghi ngay để unique index "một bản ghi đang hiệu lực" không chặn bản ghi mới.
            verificationRepository.saveAndFlush(current);
        }
        verificationRepository.save(MemberVerification.familyOwner(userId, familyId, OffsetDateTime.now(clock)));
    }

    // ---- Dùng cho API của module auth ----

    /** Schema Membership cho đăng nhập và GET /users/me/membership. */
    @Transactional(readOnly = true)
    public MembershipData describe(UUID userId, List<String> roles) {
        if (roles.contains(Role.SYSTEM_ADMIN)) {
            return MembershipData.none();
        }
        Optional<MemberVerification> active = verificationRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            MemberVerification mv = active.get();
            boolean pending = mv.getStatus() == VerificationStatus.PENDING;
            return toData(mv, pending ? MembershipStatus.PENDING : MembershipStatus.APPROVED,
                    pending ? mv.getId() : null, null, pending);
        }
        return verificationRepository
                .findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(userId, VerificationStatus.REJECTED)
                .map(mv -> toData(mv, MembershipStatus.REJECTED, null, mv.getRejectReason(), true))
                .orElseGet(MembershipData::none);
    }

    private MembershipData toData(MemberVerification mv, MembershipStatus status, UUID joinRequestId,
                                  String rejectReason, boolean canCreateFamily) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        String familyName = port == null ? null : port.findFamilyName(mv.getFamilyId()).orElse(null);
        String branchName = (port == null || mv.getBranchId() == null)
                ? null : port.findBranchName(mv.getFamilyId(), mv.getBranchId()).orElse(null);
        return new MembershipData(status, mv.getRole(), mv.getFamilyId(), familyName, mv.getBranchId(),
                branchName, joinRequestId, rejectReason, canCreateFamily);
    }

    private Optional<MemberVerification> findApproved(UUID userId) {
        return verificationRepository.findByUserIdAndStatusIn(userId, EnumSet.of(VerificationStatus.APPROVED));
    }

    private boolean isSystemAdmin(UUID userId) {
        return userRoleRepository.findRoleCodes(userId).contains(Role.SYSTEM_ADMIN);
    }
}
