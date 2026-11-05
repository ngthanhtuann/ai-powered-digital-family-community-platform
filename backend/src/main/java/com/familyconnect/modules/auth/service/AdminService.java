package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.ContentModerationPort;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentReportView;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentType;
import com.familyconnect.modules.auth.api.ContentModerationPort.ReportStatus;
import com.familyconnect.modules.auth.dto.AdminDtos.AdminContentReport;
import com.familyconnect.modules.auth.dto.AdminDtos.AdminUserData;
import com.familyconnect.modules.auth.dto.AdminDtos.GlobalRole;
import com.familyconnect.modules.auth.dto.AdminDtos.MembershipRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.AdminDtos.ReviewAction;
import com.familyconnect.modules.auth.dto.AdminDtos.UserRolesRequest;
import com.familyconnect.modules.auth.dto.AdminDtos.UserStatusRequest;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserRole;
import com.familyconnect.modules.auth.entity.UserStatus;
import com.familyconnect.modules.auth.repository.RefreshTokenRepository;
import com.familyconnect.modules.auth.repository.RoleRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quản trị hệ thống: người dùng (UC-AUTH-08) và hàng đợi kiểm duyệt (UC-AUTH-09). Chỉ SYSTEM_ADMIN.
 * Quyền đọc từ bảng user_role mỗi request (không tin vai trò trong JWT) để thu quyền có hiệu lực ngay.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MembershipService membershipService;
    private final ContentModerationPort moderation;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public AdminService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                        RoleRepository roleRepository, RefreshTokenRepository refreshTokenRepository,
                        MembershipService membershipService, ContentModerationPort moderation,
                        AuditLogger auditLogger, Clock clock) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.membershipService = membershipService;
        this.moderation = moderation;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    // ---- UC-AUTH-08: người dùng ----

    @Transactional(readOnly = true)
    public PageResponse<AdminUserData> listUsers(UUID adminId, String q, UserStatus status, GlobalRole role,
                                                 int page, int size, String sort) {
        requireSystemAdmin(adminId);
        PageRequest pageable = pageRequest(page, size, sort);
        String keyword = q == null ? "" : q.trim();
        if (keyword.length() > 100) {
            throw ApiException.invalidField("q", "Từ khóa tối đa 100 ký tự");
        }
        String pattern = "%" + keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Page<User> found = userRepository.search(pattern, status == null, status == null ? UserStatus.ACTIVE : status,
                role == null, role == null ? "" : role.name(), pageable);
        // shortcut: mỗi người một lần đọc vai trò và gia đình (tối đa 100/trang); gom theo lô nếu trang quản trị chậm.
        List<AdminUserData> data = found.getContent().stream().map(this::toData).toList();
        return PageResponse.of(data, page, size, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUserData getUser(UUID adminId, UUID userId) {
        requireSystemAdmin(adminId);
        return toData(findUser(userId));
    }

    /** Khóa: bắt buộc lý do, thu hồi mọi refresh token (BR-AUTH-13). Request kế tiếp của người đó nhận AUTH_003. */
    @Transactional
    public AdminUserData updateStatus(UUID adminId, UUID userId, UserStatusRequest request) {
        requireSystemAdmin(adminId);
        User user = findUser(userId);
        UserStatus old = user.getStatus();
        if (request.status() == UserStatus.LOCKED) {
            String reason = request.lockReason() == null ? "" : request.lockReason().trim();
            if (reason.isEmpty()) {
                throw ApiException.invalidField("lockReason", "Cần nhập lý do khóa");
            }
            if (userId.equals(adminId)) {
                throw new ApiException(ErrorCode.ADM_001);
            }
            if (userRoleRepository.findRoleCodes(userId).contains(Role.SYSTEM_ADMIN)) {
                requireAnotherActiveAdmin(userId);
            }
            user.lock(reason, adminId);
            refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now(clock));
        } else {
            user.unlock(adminId);
        }
        user = userRepository.saveAndFlush(user);
        auditLogger.log(request.status() == UserStatus.LOCKED ? "USER_LOCKED" : "USER_UNLOCKED", "USER", userId, null,
                Map.of("oldStatus", old.name(), "newStatus", user.getStatus().name()));
        return toData(user);
    }

    /** Ghi đè vai trò toàn hệ thống; luôn có MEMBER; không tự thu quyền SYSTEM_ADMIN (BR-AUTH-13). */
    @Transactional
    public AdminUserData setRoles(UUID adminId, UUID userId, UserRolesRequest request) {
        requireSystemAdmin(adminId);
        User user = findUser(userId);
        Set<String> wanted = request.roles().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
        if (!wanted.contains(Role.MEMBER)) {
            throw ApiException.invalidField("roles", "Vai trò phải luôn có MEMBER");
        }
        List<UserRole> current = userRoleRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<String> oldRoles = current.stream().map(ur -> ur.getRole().getCode()).sorted().toList();
        boolean addsAdmin = wanted.contains(Role.SYSTEM_ADMIN) && !oldRoles.contains(Role.SYSTEM_ADMIN);
        boolean removesAdmin = !wanted.contains(Role.SYSTEM_ADMIN) && oldRoles.contains(Role.SYSTEM_ADMIN);
        // BR-AUTH-08: SYSTEM_ADMIN không thuộc gia đình nào; quản trị dùng tài khoản riêng.
        if (addsAdmin && membershipService.hasActiveMembership(userId)) {
            throw ApiException.invalidField("roles",
                    "Người này đang thuộc hoặc đang xin vào một gia đình; quản trị hệ thống phải dùng tài khoản riêng");
        }
        if (removesAdmin) {
            if (userId.equals(adminId)) {
                throw new ApiException(ErrorCode.ADM_001);
            }
            requireAnotherActiveAdmin(userId);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        current.stream().filter(ur -> !wanted.contains(ur.getRole().getCode())).forEach(ur -> ur.remove(now));
        userRoleRepository.saveAllAndFlush(current);
        for (String code : wanted) {
            if (!oldRoles.contains(code)) {
                Role role = roleRepository.findByCode(code)
                        .orElseThrow(() -> new IllegalStateException("Thiếu vai trò " + code + " trong bảng role"));
                userRoleRepository.save(new UserRole(userId, role));
            }
        }
        userRoleRepository.flush();
        auditLogger.log("ROLE_CHANGED", "USER", userId, null,
                Map.of("oldRoles", oldRoles, "newRoles", List.copyOf(wanted)));
        return toData(user);
    }

    /** PUT /admin/users/{userId}/membership (BR-AUTH-14): tài khoản SYSTEM_ADMIN không gán được (400). */
    @Transactional
    public AdminUserData setMembership(UUID adminId, UUID userId, MembershipRequest request) {
        requireSystemAdmin(adminId);
        User user = findUser(userId);
        if (userRoleRepository.findRoleCodes(userId).contains(Role.SYSTEM_ADMIN)) {
            throw ApiException.invalidField("userId", "Tài khoản quản trị hệ thống không thuộc gia đình nào");
        }
        MembershipData old = membershipService.describe(userId, List.of(Role.MEMBER));
        MemberVerification mv = membershipService.assignByAdmin(userId, request.familyId(), request.branchId(),
                request.role(), adminId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("oldFamilyId", old.familyId());
        detail.put("oldRole", old.role());
        detail.put("oldBranchId", old.branchId());
        detail.put("newRole", mv.getRole());
        detail.put("newBranchId", mv.getBranchId());
        auditLogger.log("MEMBERSHIP_CHANGED", "USER", userId, mv.getFamilyId(), detail);
        return toData(user);
    }

    // ---- UC-AUTH-09: kiểm duyệt ----

    @Transactional(readOnly = true)
    public PageResponse<AdminContentReport> listReports(UUID adminId, ReportStatus status, ContentType type,
                                                        int page, int size, String sort) {
        requireSystemAdmin(adminId);
        Page<ContentReportView> found = moderation.findReports(status == null ? ReportStatus.PENDING : status, type,
                pageRequest(page, size, sort));
        return PageResponse.of(toReports(found.getContent()), page, size, found.getTotalElements());
    }

    @Transactional
    public AdminContentReport reviewReport(UUID adminId, UUID reportId, ReviewAction action) {
        requireSystemAdmin(adminId);
        ContentReportView done = switch (action) {
            case HIDE_CONTENT -> moderation.hideContent(reportId, adminId);
            case DISMISS -> moderation.dismissReport(reportId, adminId);
            case RESTORE_CONTENT -> moderation.restoreContent(reportId, adminId);
        };
        return toReports(List.of(done)).get(0);
    }

    // ---- dùng chung ----

    private List<AdminContentReport> toReports(List<ContentReportView> views) {
        Map<UUID, User> owners = userRepository.findActiveByIdIn(views.stream().map(ContentReportView::contentOwnerId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, String> families = new HashMap<>();
        return views.stream().map(v -> {
            User owner = v.contentOwnerId() == null ? null : owners.get(v.contentOwnerId());
            String familyName = families.computeIfAbsent(v.familyId(), membershipService::familyName);
            return AdminContentReport.of(v, familyName, owner == null ? null : owner.getFullName());
        }).toList();
    }

    /** Khóa các dòng quản trị đang hoạt động rồi kiểm còn người khác ngoài userId (BR-AUTH-13, chống thao tác đồng thời). */
    private void requireAnotherActiveAdmin(UUID userId) {
        if (userRepository.lockActiveAdminIds().stream().noneMatch(id -> !id.equals(userId))) {
            throw new ApiException(ErrorCode.ADM_001);
        }
    }

    private void requireSystemAdmin(UUID userId) {
        if (!userRoleRepository.findRoleCodes(userId).contains(Role.SYSTEM_ADMIN)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findActiveById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private AdminUserData toData(User user) {
        List<String> roles = userRoleRepository.findRoleCodes(user.getId());
        return AdminUserData.of(user, roles, membershipService.describe(user.getId(), roles));
    }

    /** page từ 0, size 1..100, sort chỉ createdAt (auth.yaml SortCreated). */
    static PageRequest pageRequest(int page, int size, String sort) {
        if (page < 0) {
            throw ApiException.invalidField("page", "page phải bắt đầu từ 0");
        }
        if (size < 1 || size > 100) {
            throw ApiException.invalidField("size", "size phải từ 1 đến 100");
        }
        Sort.Direction direction = switch (sort == null ? "createdAt,desc" : sort) {
            case "createdAt,desc" -> Sort.Direction.DESC;
            case "createdAt,asc" -> Sort.Direction.ASC;
            default -> throw ApiException.invalidField("sort", "Giá trị sort không hợp lệ");
        };
        return PageRequest.of(page, size, Sort.by(direction, "createdAt").and(Sort.by(direction, "id")));
    }
}
