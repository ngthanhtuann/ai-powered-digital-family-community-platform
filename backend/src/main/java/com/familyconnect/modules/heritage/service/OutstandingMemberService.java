package com.familyconnect.modules.heritage.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import com.familyconnect.modules.heritage.dto.HeritageRequests.CreateOutstandingMember;
import com.familyconnect.modules.heritage.dto.HeritageRequests.UpdateOutstandingMember;
import com.familyconnect.modules.heritage.dto.HeritageResponses.ListResponse;
import com.familyconnect.modules.heritage.dto.HeritageResponses.OutstandingMemberData;
import com.familyconnect.modules.heritage.entity.OutstandingMember;
import com.familyconnect.modules.heritage.repository.OutstandingMemberRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OutstandingMemberService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String REMOVED_PERSON = "Thành viên không còn trong gia phả";

    private final OutstandingMemberRepository repository;
    private final GenealogyQuery genealogyQuery;
    private final MembershipQuery membershipQuery;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public OutstandingMemberService(OutstandingMemberRepository repository, GenealogyQuery genealogyQuery,
                                    MembershipQuery membershipQuery, AuditLogger auditLogger, Clock clock) {
        this.repository = repository;
        this.genealogyQuery = genealogyQuery;
        this.membershipQuery = membershipQuery;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResponse<OutstandingMemberData> list(UUID requestedFamilyId, int page, int size, String sort) {
        validatePage(page, size);
        UUID familyId = resolveFamily(requestedFamilyId);
        PageRequest pageable = pageRequest(page, size, sort);
        // Xem là quyền chung của cả dòng họ (OpenAPI getOutstandingMembers); phạm vi chi chỉ áp cho thêm/sửa/xóa.
        Page<OutstandingMember> result = repository.findAllByFamilyIdAndDeletedAtIsNull(familyId, pageable);
        List<OutstandingMemberData> items = result.getContent().stream().map(this::toData).toList();
        return ListResponse.of(items, page, size, result.getTotalElements());
    }

    @Transactional
    public OutstandingMemberData create(UUID requestedFamilyId, CreateOutstandingMember request) {
        UUID familyId = resolveFamily(requestedFamilyId);
        MembershipView membership = requireAdmin(familyId);
        DirectoryPerson person = genealogyQuery.findDirectoryPerson(request.personId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (!familyId.equals(person.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        requirePersonManageScope(membership, person);
        if (repository.existsByFamilyIdAndPersonIdAndDeletedAtIsNull(familyId, person.id())) {
            throw new ApiException(ErrorCode.HER_001);
        }

        UUID actorId = CurrentUser.require().id();
        OutstandingMember saved;
        try {
            saved = repository.saveAndFlush(new OutstandingMember(familyId, person.id(),
                    request.achievement().trim(), request.displayOrder() == null ? 0 : request.displayOrder(),
                    actorId));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateOutstandingPerson(exception)) {
                throw new ApiException(ErrorCode.HER_001);
            }
            throw exception;
        }
        auditLogger.log("OUTSTANDING_MEMBER_CREATED", "OUTSTANDING_MEMBER", saved.getId(), familyId,
                Map.of("personId", person.id()));
        return toData(saved, person.fullName());
    }

    @Transactional
    public OutstandingMemberData update(UUID id, UpdateOutstandingMember request) {
        OutstandingMember member = find(id);
        DirectoryPerson person = requireManageScope(member);
        if (request.achievement() == null && request.displayOrder() == null) {
            throw ApiException.invalidField("body", "Cần ít nhất một trường để cập nhật");
        }
        if (request.achievement() != null && request.achievement().isBlank()) {
            throw ApiException.invalidField("achievement", "Thành tích không được để trống");
        }
        UUID actorId = CurrentUser.require().id();
        member.update(request.achievement() == null ? null : request.achievement().trim(),
                request.displayOrder(), actorId);
        OutstandingMember saved = repository.saveAndFlush(member);
        auditLogger.log("OUTSTANDING_MEMBER_UPDATED", "OUTSTANDING_MEMBER", saved.getId(),
                saved.getFamilyId(), Map.of());
        return toData(saved, person == null ? REMOVED_PERSON : person.fullName());
    }

    @Transactional
    public void delete(UUID id) {
        OutstandingMember member = find(id);
        requireManageScope(member);
        UUID actorId = CurrentUser.require().id();
        member.delete(OffsetDateTime.now(clock), actorId);
        repository.saveAndFlush(member);
        auditLogger.log("OUTSTANDING_MEMBER_DELETED", "OUTSTANDING_MEMBER", member.getId(),
                member.getFamilyId(), Map.of());
    }

    private UUID resolveFamily(UUID requestedFamilyId) {
        UUID actorId = CurrentUser.require().id();
        MembershipView membership = membershipQuery.getMembership(actorId);
        if (membership.role() == EffectiveRole.SYSTEM_ADMIN) {
            if (requestedFamilyId == null) {
                throw ApiException.invalidField("familyId", "SYSTEM_ADMIN phải truyền familyId");
            }
            if (!genealogyQuery.familyExists(requestedFamilyId)) {
                throw new ApiException(ErrorCode.NOT_FOUND_001);
            }
            return requestedFamilyId;
        }
        membershipQuery.requireVerified(actorId);
        if (membership.familyId() == null) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        if (requestedFamilyId != null && !requestedFamilyId.equals(membership.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        membershipQuery.requireFamilyAccess(actorId, membership.familyId());
        return membership.familyId();
    }

    private MembershipView requireAdmin(UUID familyId) {
        UUID actorId = CurrentUser.require().id();
        MembershipView membership = membershipQuery.getMembership(actorId);
        membershipQuery.requireFamilyAccess(actorId, familyId);
        if (membership.role() != EffectiveRole.BRANCH_ADMIN
                && membership.role() != EffectiveRole.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        return membership;
    }

    /**
     * Quyền sửa/xóa một mục. Người đã bị xóa khỏi gia phả thì không còn chi để đối chiếu: trưởng họ và quản trị
     * vẫn dọn được mục đó, trưởng chi nhận PERM_001. Trả null khi người không còn.
     */
    private DirectoryPerson requireManageScope(OutstandingMember member) {
        MembershipView membership = requireAdmin(member.getFamilyId());
        DirectoryPerson person = genealogyQuery.findDirectoryPerson(member.getPersonId()).orElse(null);
        if (person != null) {
            requirePersonManageScope(membership, person);
        } else if (membership.role() != EffectiveRole.SYSTEM_ADMIN && membership.branchId() != null) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        return person;
    }

    private void requirePersonManageScope(MembershipView membership, DirectoryPerson person) {
        if (membership.role() == EffectiveRole.SYSTEM_ADMIN || membership.branchId() == null) {
            return;
        }
        if (person.branchId() == null
                || !genealogyQuery.isBranchWithin(person.familyId(), person.branchId(), membership.branchId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private OutstandingMember find(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private OutstandingMemberData toData(OutstandingMember member) {
        String fullName = genealogyQuery.findDirectoryPerson(member.getPersonId())
                .map(DirectoryPerson::fullName)
                .orElse(REMOVED_PERSON);
        return toData(member, fullName);
    }

    private static OutstandingMemberData toData(OutstandingMember member, String fullName) {
        return new OutstandingMemberData(member.getId(), member.getPersonId(), fullName,
                member.getAchievement(), member.getDisplayOrder(), member.getCreatedAt(), member.getUpdatedAt());
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw ApiException.invalidField("page", "page phải bắt đầu từ 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.invalidField("size", "size phải từ 1 đến 100");
        }
    }

    private static boolean isDuplicateOutstandingPerson(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_outstanding_person".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static PageRequest pageRequest(int page, int size, String requestedSort) {
        String sort = requestedSort == null || requestedSort.isBlank() ? "displayOrder,asc" : requestedSort;
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !List.of("displayOrder", "createdAt").contains(parts[0])
                || !List.of("asc", "desc").contains(parts[1].toLowerCase())) {
            throw ApiException.invalidField("sort",
                    "Chỉ hỗ trợ displayOrder,asc|desc hoặc createdAt,asc|desc");
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, parts[0]).and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
