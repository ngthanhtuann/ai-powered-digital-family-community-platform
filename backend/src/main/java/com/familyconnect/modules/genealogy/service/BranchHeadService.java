package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.MembershipCommand;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.BranchHeadData;
import com.familyconnect.modules.genealogy.entity.Branch;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** BR-GEN-29 (SCRUM-146): trưởng chi của từng chi, chọn tay (kể cả người chưa có tài khoản) hoặc theo dòng (BR-GEN-25). */
@Service
public class BranchHeadService {

    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final LineageService lineageService;
    private final GenealogyAccess access;
    private final MembershipCommand membershipCommand;
    private final AuditLogger auditLogger;

    public BranchHeadService(BranchRepository branchRepository, PersonRepository personRepository, LineageService lineageService,
                             GenealogyAccess access, MembershipCommand membershipCommand, AuditLogger auditLogger) {
        this.branchRepository = branchRepository;
        this.personRepository = personRepository;
        this.lineageService = lineageService;
        this.access = access;
        this.membershipCommand = membershipCommand;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<BranchHeadData> heads(UUID familyId) {
        access.requireRead(familyId);
        UUID familyHead = lineageService.familyHolder(familyId).orElse(null);
        Map<UUID, Person> people = new HashMap<>();
        return branchRepository.findAllByFamilyIdAndDeletedAtIsNullOrderByName(familyId).stream()
                .map(b -> toData(b, familyHead, people)).toList();
    }

    @Transactional
    public BranchHeadData setHead(UUID branchId, UUID personId) {
        Branch branch = branchRepository.findById(branchId).filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        UUID familyId = branch.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, null); // trưởng họ hoặc SYSTEM_ADMIN
        Person head = null;
        if (personId != null) {
            head = personRepository.findByIdAndDeletedAtIsNull(personId)
                    .filter(p -> familyId.equals(p.getFamilyId()))
                    .orElseThrow(() -> ApiException.invalidField("personId", "Người này không thuộc dòng họ"));
            if (head.isDeceased()) {
                throw ApiException.invalidField("personId", "Không chọn người đã mất làm trưởng chi");
            }
        }
        branch.setHeadPersonId(personId);
        branchRepository.saveAndFlush(branch);
        auditLogger.log("BRANCH_HEAD_SET", "BRANCH", branchId, familyId,
                personId == null ? Map.of("source", "LINEAGE") : Map.of("headPersonId", personId));
        if (head != null && head.getUserId() != null) {
            membershipCommand.grantBranchHead(head.getUserId(), familyId, branchId, viewer.userId()); // BR-AUTH-26
        }
        return toData(branch, lineageService.familyHolder(familyId).orElse(null), new HashMap<>());
    }

    private BranchHeadData toData(Branch b, UUID familyHead, Map<UUID, Person> cache) {
        UUID headId = lineageService.branchHead(b.getHeadPersonId(), b.getOriginPersonId());
        Person head = headId == null ? null
                : cache.computeIfAbsent(headId, id -> personRepository.findByIdAndDeletedAtIsNull(id).orElse(null));
        String source = head == null ? null : b.getHeadPersonId() != null ? "MANUAL" : "LINEAGE";
        return new BranchHeadData(b.getId(), b.getName(), b.getParentBranchId(), head == null ? null : head.getId(),
                head == null ? null : head.getFullName(), source, head != null && head.getUserId() != null,
                head != null && head.getId().equals(familyHead));
    }
}
