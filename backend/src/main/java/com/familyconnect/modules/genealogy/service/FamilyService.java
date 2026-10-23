package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.MembershipCommand;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.FamilyInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.FamilyData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.ListResponse;
import com.familyconnect.modules.genealogy.entity.Family;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UC-GEN-01: tạo, xem, sửa, xóa gia đình (BR-GEN-09, 11, 14, 15). */
@Service
public class FamilyService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");

    private final FamilyRepository familyRepository;
    private final PersonRepository personRepository;
    private final GenealogyAccess access;
    private final MembershipCommand membershipCommand;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public FamilyService(FamilyRepository familyRepository, PersonRepository personRepository,
                         GenealogyAccess access, MembershipCommand membershipCommand,
                         AuditLogger auditLogger, Clock clock) {
        this.familyRepository = familyRepository;
        this.personRepository = personRepository;
        this.access = access;
        this.membershipCommand = membershipCommand;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** Gia đình của tôi (tối đa 1, BR-GEN-15); SYSTEM_ADMIN thấy mọi gia đình để quản trị. */
    @Transactional(readOnly = true)
    public ListResponse<FamilyData> list(int page, int size, String sort) {
        Pageable pageable = Paging.of(page, size, sort, SORT_FIELDS);
        Viewer viewer = access.current();
        Page<Family> found;
        if (viewer.isSystemAdmin()) {
            found = familyRepository.findAllByDeletedAtIsNull(pageable);
        } else if (viewer.membership().verified() && viewer.membership().familyId() != null) {
            found = familyRepository.findAllByIdInAndDeletedAtIsNull(List.of(viewer.membership().familyId()), pageable);
        } else {
            found = Page.empty(pageable);
        }
        return ListResponse.of(found.map(FamilyService::toData).getContent(), page, size, found.getTotalElements());
    }

    @Transactional
    public FamilyData create(FamilyInput input) {
        Viewer viewer = access.current();
        if (!viewer.isSystemAdmin() && viewer.membership().verified() && viewer.membership().familyId() != null) {
            throw new ApiException(ErrorCode.GEN_013);
        }
        Family family = new Family();
        family.setName(input.name().trim());
        family.setDescription(blankToNull(input.description()));
        family.setCreatedBy(viewer.userId());
        familyRepository.saveAndFlush(family);
        if (!viewer.isSystemAdmin()) {
            // Cùng giao dịch: người tạo thành BRANCH_ADMIN cả gia đình (UC-GEN-01 bước 4, BR-AUTH-12).
            membershipCommand.assignFamilyOwner(viewer.userId(), family.getId());
        }
        auditLogger.log("FAMILY_CREATED", "FAMILY", family.getId(), family.getId(), Map.of());
        return toData(family);
    }

    @Transactional(readOnly = true)
    public FamilyData get(UUID familyId) {
        access.requireRead(familyId);
        return toData(find(familyId));
    }

    @Transactional
    public FamilyData update(UUID familyId, FamilyInput input) {
        Viewer viewer = access.requireRead(familyId);
        Family family = find(familyId);
        access.requireManage(viewer, familyId, null);
        family.setName(input.name().trim());
        family.setDescription(blankToNull(input.description()));
        family.setUpdatedBy(viewer.userId());
        familyRepository.saveAndFlush(family);
        auditLogger.log("FAMILY_UPDATED", "FAMILY", familyId, familyId, Map.of());
        return toData(family);
    }

    @Transactional
    public void delete(UUID familyId) {
        Viewer viewer = access.requireRead(familyId);
        Family family = find(familyId);
        if (!viewer.isSystemAdmin()) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (personRepository.countByFamilyIdAndDeletedAtIsNull(familyId) > 0) {
            throw new ApiException(ErrorCode.GEN_001);
        }
        family.setDeletedAt(OffsetDateTime.now(clock));
        family.setUpdatedBy(viewer.userId());
        auditLogger.log("FAMILY_DELETED", "FAMILY", familyId, familyId, Map.of());
    }

    private Family find(UUID familyId) {
        return familyRepository.findByIdAndDeletedAtIsNull(familyId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static FamilyData toData(Family family) {
        return new FamilyData(family.getId(), family.getName(), family.getDescription(), family.getCreatedBy(),
                family.getCreatedAt(), family.getUpdatedAt());
    }
}
