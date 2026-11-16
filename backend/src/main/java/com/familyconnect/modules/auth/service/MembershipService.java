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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

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

    // ---- Gán, đổi vai trò trong gia đình (BR-AUTH-14, BR-AUTH-22) ----

    /** Gia đình và chi (nếu có) phải tồn tại, chi phải thuộc gia đình; không thì 404 NOT_FOUND_001. */
    public void requireFamilyAndBranch(UUID familyId, UUID branchId) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        if (port == null || port.findFamilyName(familyId).isEmpty()
                || (branchId != null && port.findBranchName(familyId, branchId).isEmpty())) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
    }

    /**
     * PUT /admin/users/{userId}/membership: có hiệu lực ngay. Cùng gia đình thì đổi vai trò, chi tại chỗ; khác gia đình
     * thì bản ghi cũ REMOVED và gỡ liên kết hồ sơ; yêu cầu PENDING thì CANCELLED. Người gọi đã kiểm người dùng không
     * phải SYSTEM_ADMIN.
     */
    @Transactional
    public MemberVerification assignByAdmin(UUID userId, UUID familyId, UUID branchId, MemberRole role, UUID actor) {
        requireFamilyAndBranch(familyId, branchId);
        Optional<MemberVerification> active = verificationRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            MemberVerification current = active.get();
            if (current.getStatus() == VerificationStatus.APPROVED && current.getFamilyId().equals(familyId)) {
                return changeRole(current, role, branchId, actor);
            }
            if (current.getStatus() == VerificationStatus.PENDING) {
                current.cancel(actor);
            } else {
                requireAnotherFamilyHead(current);
                current.remove(actor);
            }
            // Ghi ngay để unique index "một bản ghi đang hiệu lực" không chặn bản ghi mới.
            verificationRepository.saveAndFlush(current);
            if (current.getStatus() == VerificationStatus.REMOVED) {
                GenealogyPort port = genealogyPort.getIfAvailable();
                if (port != null) {
                    port.unlinkAccount(userId);
                }
            }
        }
        return verificationRepository.saveAndFlush(
                MemberVerification.assigned(userId, familyId, branchId, role, actor, OffsetDateTime.now(clock)));
    }

    /** Đổi vai trò, chi trong cùng gia đình; gia đình luôn còn ít nhất một trưởng họ (ADM_001). */
    @Transactional
    public MemberVerification changeRole(MemberVerification mv, MemberRole role, UUID branchId, UUID actor) {
        boolean staysHead = role == MemberRole.BRANCH_ADMIN && branchId == null;
        if (!staysHead) {
            requireAnotherFamilyHead(mv);
        }
        mv.changeRole(role, branchId, actor);
        return verificationRepository.saveAndFlush(mv);
    }

    private void requireAnotherFamilyHead(MemberVerification mv) {
        if (mv.isFamilyHead() && verificationRepository.lockFamilyHeads(mv.getFamilyId()).stream()
                .noneMatch(other -> !other.getUserId().equals(mv.getUserId()))) {
            throw new ApiException(ErrorCode.ADM_001, "Dòng họ phải còn ít nhất một trưởng họ");
        }
    }

    /** Bản ghi đang hiệu lực (PENDING hoặc APPROVED), dùng cho BR-AUTH-08 khi thêm SYSTEM_ADMIN. */
    @Transactional(readOnly = true)
    public boolean hasActiveMembership(UUID userId) {
        return verificationRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES).isPresent();
    }

    /** Schema FamilyAccount: tên chi (null nếu không có chi hoặc chưa có module Gia phả). */
    public String branchName(UUID familyId, UUID branchId) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        return port == null || branchId == null ? null : port.findBranchName(familyId, branchId).orElse(null);
    }

    public String familyName(UUID familyId) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        return port == null ? null : port.findFamilyName(familyId).orElse(null);
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

    /**
     * Như {@link #describe} cho cả một trang người dùng (danh sách quản trị): đọc bản xác minh của cả trang trong
     * hai truy vấn, tên gia đình và chi mỗi giá trị đọc một lần, thay vì tới 5 truy vấn cho từng người.
     */
    @Transactional(readOnly = true)
    public Map<UUID, MembershipData> describeAll(Map<UUID, List<String>> rolesByUser) {
        List<UUID> members = rolesByUser.entrySet().stream()
                .filter(e -> !e.getValue().contains(Role.SYSTEM_ADMIN)).map(Map.Entry::getKey).toList();
        Map<UUID, MemberVerification> active = members.isEmpty() ? Map.of()
                : verificationRepository.findByUserIdInAndStatusIn(members, ACTIVE_STATUSES).stream()
                        .collect(Collectors.toMap(MemberVerification::getUserId, mv -> mv, (a, b) -> a));
        List<UUID> others = members.stream().filter(id -> !active.containsKey(id)).toList();
        Map<UUID, MemberVerification> rejected = others.isEmpty() ? Map.of()
                : verificationRepository.findByUserIdInAndStatusAndDeletedAtIsNull(others, VerificationStatus.REJECTED)
                        .stream().collect(Collectors.toMap(MemberVerification::getUserId, mv -> mv,
                                BinaryOperator.maxBy(Comparator.comparing(MemberVerification::getUpdatedAt,
                                        Comparator.nullsFirst(Comparator.naturalOrder())))));
        Map<UUID, Optional<String>> familyNames = new HashMap<>();
        Map<List<UUID>, Optional<String>> branchNames = new HashMap<>();
        Map<UUID, MembershipData> result = new HashMap<>();
        for (UUID userId : rolesByUser.keySet()) {
            MemberVerification mv = active.get(userId);
            MembershipData data;
            if (mv != null) {
                boolean pending = mv.getStatus() == VerificationStatus.PENDING;
                data = toData(mv, pending ? MembershipStatus.PENDING : MembershipStatus.APPROVED,
                        pending ? mv.getId() : null, null, pending, familyNames, branchNames);
            } else if ((mv = rejected.get(userId)) != null) {
                data = toData(mv, MembershipStatus.REJECTED, null, mv.getRejectReason(), true, familyNames, branchNames);
            } else {
                data = MembershipData.none();
            }
            result.put(userId, data);
        }
        return result;
    }

    private MembershipData toData(MemberVerification mv, MembershipStatus status, UUID joinRequestId,
                                  String rejectReason, boolean canCreateFamily) {
        return toData(mv, status, joinRequestId, rejectReason, canCreateFamily, new HashMap<>(), new HashMap<>());
    }

    private MembershipData toData(MemberVerification mv, MembershipStatus status, UUID joinRequestId,
                                  String rejectReason, boolean canCreateFamily,
                                  Map<UUID, Optional<String>> familyNames, Map<List<UUID>, Optional<String>> branchNames) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        String familyName = port == null ? null
                : familyNames.computeIfAbsent(mv.getFamilyId(), port::findFamilyName).orElse(null);
        String branchName = (port == null || mv.getBranchId() == null) ? null
                : branchNames.computeIfAbsent(List.of(mv.getFamilyId(), mv.getBranchId()),
                        key -> port.findBranchName(key.get(0), key.get(1))).orElse(null);
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
