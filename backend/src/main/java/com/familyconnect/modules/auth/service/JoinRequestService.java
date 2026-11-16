package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.CreateRequest;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.Decision;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.JoinRequestData;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.ReviewRequest;
import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import com.familyconnect.modules.auth.repository.MemberVerificationRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Xin tham gia gia đình và duyệt (UC-AUTH-06, 07; BR-AUTH-08 đến 11). */
@Service
public class JoinRequestService {

    private final MembershipService membershipService;
    private final MemberVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<GenealogyPort> genealogyPort;
    private final AuditLogger auditLogger;
    private final SystemConfigQuery config;
    private final Clock clock;

    public JoinRequestService(MembershipService membershipService, MemberVerificationRepository verificationRepository,
                              UserRepository userRepository, ObjectProvider<GenealogyPort> genealogyPort,
                              AuditLogger auditLogger, SystemConfigQuery config, Clock clock) {
        this.membershipService = membershipService;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.genealogyPort = genealogyPort;
        this.auditLogger = auditLogger;
        this.config = config;
        this.clock = clock;
    }

    /** POST /join-requests. Cấu hình membership.approval_mode = AUTO thì duyệt ngay (UC-AUTH-06 4a, SCRUM-94). */
    @Transactional
    public JoinRequestData create(UUID userId, CreateRequest request) {
        if (membershipService.getMembership(userId).role() == EffectiveRole.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        // Mã gia đình: vào cả dòng họ; mã chi: vào chi đó. Không khớp: cùng một 404, không lộ loại mã.
        UUID familyId;
        UUID branchId = null;
        if (membershipService.familyName(request.code()) != null) {
            familyId = request.code();
        } else {
            familyId = genealogy().findBranchFamily(request.code())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001, "Mã gia đình hoặc mã chi không đúng"));
            branchId = request.code();
            membershipService.requireFamilyAndBranch(familyId, branchId);
        }
        if (membershipService.hasActiveMembership(userId)) {
            throw new ApiException(ErrorCode.AUTH_005);
        }
        String message = request.message() == null || request.message().isBlank() ? null : request.message().trim();
        MemberVerification mv;
        try {
            mv = verificationRepository.saveAndFlush(
                    MemberVerification.joinRequest(userId, familyId, branchId, message));
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng lúc: chỉ mục "một bản ghi đang hiệu lực mỗi người" chặn bản thứ hai.
            throw new ApiException(ErrorCode.AUTH_005);
        }
        auditLogger.log("JOIN_REQUESTED", "JOIN_REQUEST", mv.getId(), mv.getFamilyId(), Map.of());
        if ("AUTO".equals(config.getString(SystemConfigQuery.MEMBERSHIP_APPROVAL_MODE))) {
            // Không có người duyệt: reviewed_by để trống, nhật ký ghi rõ là duyệt tự động.
            mv.approve(null, OffsetDateTime.now(clock));
            verificationRepository.saveAndFlush(mv);
            auditLogger.log("JOIN_APPROVED", "JOIN_REQUEST", mv.getId(), mv.getFamilyId(), Map.of("auto", true));
        }
        return toData(mv);
    }

    /** GET /join-requests: phạm vi theo BR-AUTH-10. */
    @Transactional(readOnly = true)
    public PageResponse<JoinRequestData> list(UUID actorId, VerificationStatus status, UUID familyId, int page,
                                              int size, String sort) {
        MembershipView actor = requireReviewer(actorId);
        PageRequest pageable = AdminService.pageRequest(page, size, sort);
        boolean admin = actor.role() == EffectiveRole.SYSTEM_ADMIN;
        UUID family = admin ? familyId : actor.familyId();
        // shortcut: lọc phạm vi chi trong bộ nhớ (mỗi gia đình ít yêu cầu); chuyển sang SQL nếu danh sách lớn.
        List<MemberVerification> visible = verificationRepository
                .findRequests(status == null ? VerificationStatus.PENDING : status, family == null, family,
                        pageable.getSort())
                .stream().filter(mv -> inScope(actor, mv)).toList();
        int from = (int) Math.min(pageable.getOffset(), visible.size());
        List<MemberVerification> slice = visible.subList(from, Math.min(from + size, visible.size()));
        Map<UUID, User> users = userRepository.findActiveByIdIn(slice.stream().map(MemberVerification::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(slice.stream().map(mv -> JoinRequestData.of(mv, users.get(mv.getUserId()))).toList(),
                page, size, visible.size());
    }

    /** PATCH /join-requests/{id}: chỉ yêu cầu PENDING trong phạm vi; gắn hồ sơ (nếu có) cùng giao dịch. */
    @Transactional
    public JoinRequestData review(UUID actorId, UUID requestId, ReviewRequest request) {
        MembershipView actor = requireReviewer(actorId);
        MemberVerification mv = verificationRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (!inScope(actor, mv)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (mv.getStatus() != VerificationStatus.PENDING) {
            throw new ApiException(ErrorCode.AUTH_006);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> detail = new HashMap<>();
        if (request.status() == Decision.APPROVED) {
            if (request.branchId() != null) {
                requireAssignableBranch(actor, mv.getFamilyId(), request.branchId());
            }
            if (request.personId() != null) {
                // Gắn hồ sơ trước: lỗi GEN_xxx thì chưa đổi gì, yêu cầu vẫn PENDING (UC-AUTH-07 4a).
                genealogy().linkAccount(mv.getFamilyId(), request.personId(), mv.getUserId());
                detail.put("personId", request.personId());
            }
            if (request.branchId() != null) {
                mv.changeRole(mv.getRole(), request.branchId(), actorId);
                detail.put("branchId", request.branchId());
            }
            mv.approve(actorId, now);
            verificationRepository.saveAndFlush(mv);
            auditLogger.log("JOIN_APPROVED", "JOIN_REQUEST", mv.getId(), mv.getFamilyId(), detail);
        } else {
            String reason = request.rejectReason() == null || request.rejectReason().isBlank()
                    ? null : request.rejectReason().trim();
            mv.reject(reason, actorId, now);
            verificationRepository.saveAndFlush(mv);
            auditLogger.log("JOIN_REJECTED", "JOIN_REQUEST", mv.getId(), mv.getFamilyId(), detail);
        }
        return toData(mv);
    }

    /** DELETE /join-requests/{id}: chỉ người xin hủy yêu cầu PENDING của mình. */
    @Transactional
    public void cancel(UUID userId, UUID requestId) {
        MemberVerification mv = verificationRepository.findByIdAndDeletedAtIsNull(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (!mv.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (mv.getStatus() != VerificationStatus.PENDING) {
            throw new ApiException(ErrorCode.AUTH_006);
        }
        mv.cancel(userId);
        verificationRepository.saveAndFlush(mv);
        auditLogger.log("JOIN_CANCELLED", "JOIN_REQUEST", mv.getId(), mv.getFamilyId(), Map.of());
    }

    /** SYSTEM_ADMIN hoặc BRANCH_ADMIN đã xác minh; chưa xác minh PERM_002, thành viên thường PERM_001. */
    private MembershipView requireReviewer(UUID actorId) {
        MembershipView view = membershipService.getMembership(actorId);
        if (view.role() == EffectiveRole.SYSTEM_ADMIN) {
            return view;
        }
        if (!view.verified()) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        if (view.role() != EffectiveRole.BRANCH_ADMIN) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        return view;
    }

    /**
     * BR-AUTH-10: cùng gia đình; người quản lý cả gia đình xử lý mọi yêu cầu; trưởng chi chỉ xử lý yêu cầu chọn chi
     * mình hoặc chi con; yêu cầu không chọn chi chỉ người quản lý cả gia đình và SYSTEM_ADMIN.
     */
    private boolean inScope(MembershipView actor, MemberVerification mv) {
        if (actor.role() == EffectiveRole.SYSTEM_ADMIN) {
            return true;
        }
        if (!mv.getFamilyId().equals(actor.familyId())) {
            return false;
        }
        if (actor.branchId() == null) {
            return true;
        }
        return mv.getBranchId() != null && genealogy().isBranchWithin(mv.getFamilyId(), mv.getBranchId(), actor.branchId());
    }

    /** Chi phải thuộc gia đình (404); trưởng chi chỉ đặt chi mình hoặc chi con (403 PERM_001). */
    private void requireAssignableBranch(MembershipView actor, UUID familyId, UUID branchId) {
        membershipService.requireFamilyAndBranch(familyId, branchId);
        if (actor.role() == EffectiveRole.BRANCH_ADMIN && actor.branchId() != null
                && !genealogy().isBranchWithin(familyId, branchId, actor.branchId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private GenealogyPort genealogy() {
        GenealogyPort port = genealogyPort.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("Thiếu module Gia phả (GenealogyPort)");
        }
        return port;
    }

    private JoinRequestData toData(MemberVerification mv) {
        return JoinRequestData.of(mv, userRepository.findActiveById(mv.getUserId()).orElse(null));
    }
}
