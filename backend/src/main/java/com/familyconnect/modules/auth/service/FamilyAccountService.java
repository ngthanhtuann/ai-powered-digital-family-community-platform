package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.dto.AdminDtos.FamilyAccount;
import com.familyconnect.modules.auth.dto.AdminDtos.FamilyAccountRoleRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import com.familyconnect.modules.auth.repository.MemberVerificationRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Trưởng họ bổ nhiệm, thu quyền trưởng chi trong dòng họ (UC-AUTH-14, BR-AUTH-22). */
@Service
public class FamilyAccountService {

    private final MembershipService membershipService;
    private final MemberVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final AuditLogger auditLogger;

    public FamilyAccountService(MembershipService membershipService, MemberVerificationRepository verificationRepository,
                                UserRepository userRepository, AuditLogger auditLogger) {
        this.membershipService = membershipService;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public PageResponse<FamilyAccount> list(UUID actorId, UUID familyId, int page, int size) {
        requireFamilyHead(actorId, familyId);
        if (page < 0) {
            throw ApiException.invalidField("page", "page phải bắt đầu từ 0");
        }
        if (size < 1 || size > 100) {
            throw ApiException.invalidField("size", "size phải từ 1 đến 100");
        }
        Page<MemberVerification> found = verificationRepository.findApprovedInFamily(familyId, PageRequest.of(page, size));
        Map<UUID, String> names = userRepository.findActiveByIdIn(
                        found.getContent().stream().map(MemberVerification::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        Map<UUID, String> branches = new HashMap<>();
        Function<MemberVerification, FamilyAccount> view = mv -> toAccount(mv, names.get(mv.getUserId()),
                mv.getBranchId() == null ? null
                        : branches.computeIfAbsent(mv.getBranchId(), b -> membershipService.branchName(familyId, b)));
        List<FamilyAccount> data = found.getContent().stream().map(view).toList();
        return PageResponse.of(data, page, size, found.getTotalElements());
    }

    @Transactional
    public FamilyAccount setRole(UUID actorId, UUID familyId, UUID userId, FamilyAccountRoleRequest request) {
        requireFamilyHead(actorId, familyId);
        if (userId.equals(actorId)) {
            throw new ApiException(ErrorCode.ADM_001, "Không thể tự đổi vai trò của chính mình");
        }
        MemberVerification mv = verificationRepository
                .findByUserIdAndStatusIn(userId, EnumSet.of(VerificationStatus.APPROVED))
                .filter(found -> found.getFamilyId().equals(familyId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        User user = userRepository.findActiveById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        membershipService.requireFamilyAndBranch(familyId, request.branchId());
        Map<String, Object> detail = new HashMap<>();
        detail.put("oldRole", mv.getRole());
        detail.put("oldBranchId", mv.getBranchId());
        mv = membershipService.changeRole(mv, request.role(), request.branchId(), actorId);
        detail.put("newRole", mv.getRole());
        detail.put("newBranchId", mv.getBranchId());
        auditLogger.log("MEMBERSHIP_CHANGED", "USER", userId, familyId, detail);
        return toAccount(mv, user.getFullName(), membershipService.branchName(familyId, mv.getBranchId()));
    }

    /** Trưởng họ của chính dòng họ này (BRANCH_ADMIN không gán chi) hoặc SYSTEM_ADMIN; chưa xác minh là PERM_002. */
    private void requireFamilyHead(UUID actorId, UUID familyId) {
        MembershipView view = membershipService.getMembership(actorId);
        if (view.role() == EffectiveRole.SYSTEM_ADMIN) {
            return;
        }
        if (!view.verified()) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        if (view.role() != EffectiveRole.BRANCH_ADMIN || view.branchId() != null || !familyId.equals(view.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private static FamilyAccount toAccount(MemberVerification mv, String fullName, String branchName) {
        return new FamilyAccount(mv.getUserId(), fullName, mv.getRole(), mv.getBranchId(), branchName);
    }
}
