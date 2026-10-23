package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.BranchInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.BranchData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.ListResponse;
import com.familyconnect.modules.genealogy.entity.Branch;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** UC-GEN-02: chi họ (BR-GEN-01, 09, 12, 14). */
@Service
public class BranchService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");

    private final BranchRepository branchRepository;
    private final FamilyRepository familyRepository;
    private final PersonRepository personRepository;
    private final GenealogyAccess access;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public BranchService(BranchRepository branchRepository, FamilyRepository familyRepository,
                         PersonRepository personRepository, GenealogyAccess access, AuditLogger auditLogger,
                         Clock clock) {
        this.branchRepository = branchRepository;
        this.familyRepository = familyRepository;
        this.personRepository = personRepository;
        this.access = access;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResponse<BranchData> list(UUID familyId, int page, int size, String sort) {
        access.requireRead(familyId);
        requireFamily(familyId);
        Page<Branch> found = branchRepository.findAllByFamilyIdAndDeletedAtIsNull(familyId,
                Paging.of(page, size, sort, SORT_FIELDS));
        return ListResponse.of(found.map(BranchService::toData).getContent(), page, size, found.getTotalElements());
    }

    @Transactional
    public BranchData create(UUID familyId, BranchInput input) {
        Viewer viewer = access.requireRead(familyId);
        requireFamily(familyId);
        // Chi gốc cần quyền cả gia đình; chi con cần quyền trên chi cha.
        access.requireManage(viewer, familyId, input.parentBranchId());
        validateReferences(familyId, input);
        Branch branch = new Branch();
        branch.setFamilyId(familyId);
        apply(branch, input);
        branch.setCreatedBy(viewer.userId());
        branchRepository.saveAndFlush(branch);
        auditLogger.log("BRANCH_CREATED", "BRANCH", branch.getId(), familyId, Map.of());
        return toData(branch);
    }

    @Transactional
    public BranchData update(UUID branchId, BranchInput input) {
        Branch branch = find(branchId);
        UUID familyId = branch.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, branchId);
        validateReferences(familyId, input);
        UUID newParent = input.parentBranchId();
        if (newParent != null && (newParent.equals(branchId) || branchRepository.isWithin(familyId, newParent, branchId))) {
            throw new ApiException(ErrorCode.GEN_003); // BR-GEN-12
        }
        if (!Objects.equals(newParent, branch.getParentBranchId())) {
            access.requireManage(viewer, familyId, newParent);
        }
        apply(branch, input);
        branch.setUpdatedBy(viewer.userId());
        branchRepository.saveAndFlush(branch);
        auditLogger.log("BRANCH_UPDATED", "BRANCH", branchId, familyId, Map.of());
        return toData(branch);
    }

    @Transactional
    public void delete(UUID branchId) {
        Branch branch = find(branchId);
        UUID familyId = branch.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, branchId);
        if (personRepository.countByBranchIdAndDeletedAtIsNull(branchId) > 0
                || branchRepository.countByParentBranchIdAndDeletedAtIsNull(branchId) > 0) {
            throw new ApiException(ErrorCode.GEN_002); // BR-GEN-09
        }
        branch.setDeletedAt(OffsetDateTime.now(clock));
        branch.setUpdatedBy(viewer.userId());
        auditLogger.log("BRANCH_DELETED", "BRANCH", branchId, familyId, Map.of());
    }

    /** Chi cha và người tổ phải thuộc cùng gia đình (UC-GEN-02 luồng 2b). */
    private void validateReferences(UUID familyId, BranchInput input) {
        if (input.parentBranchId() != null
                && branchRepository.findByIdAndFamilyIdAndDeletedAtIsNull(input.parentBranchId(), familyId).isEmpty()) {
            throw ApiException.invalidField("parentBranchId", "Chi cha không thuộc gia đình này");
        }
        if (input.originPersonId() != null && personRepository.findByIdAndDeletedAtIsNull(input.originPersonId())
                .filter(p -> p.getFamilyId().equals(familyId)).isEmpty()) {
            throw ApiException.invalidField("originPersonId", "Người tổ không thuộc gia đình này");
        }
    }

    private void requireFamily(UUID familyId) {
        if (familyRepository.findByIdAndDeletedAtIsNull(familyId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
    }

    private Branch find(UUID branchId) {
        return branchRepository.findByIdAndDeletedAtIsNull(branchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private static void apply(Branch branch, BranchInput input) {
        branch.setName(input.name().trim());
        branch.setParentBranchId(input.parentBranchId());
        branch.setOriginPersonId(input.originPersonId());
        branch.setDescription(FamilyService.blankToNull(input.description()));
    }

    private static BranchData toData(Branch branch) {
        return new BranchData(branch.getId(), branch.getFamilyId(), branch.getName(), branch.getParentBranchId(),
                branch.getOriginPersonId(), branch.getDescription(), branch.getCreatedAt(), branch.getUpdatedAt());
    }
}
