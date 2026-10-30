package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.MarriageInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.MarriageStatus;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.MarriageUpdate;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.ParentInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.ParentKind;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.MarriageData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.ParentChildData;
import com.familyconnect.modules.genealogy.entity.Marriage;
import com.familyconnect.modules.genealogy.entity.ParentChild;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.MarriageRepository;
import com.familyconnect.modules.genealogy.repository.ParentChildRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * UC-GEN-04 (quan hệ cha-mẹ-con) và UC-GEN-05 (hôn nhân): BR-GEN-02 đến 08, BR-GEN-14.
 * Chỉ BRANCH_ADMIN trong phạm vi chi và SYSTEM_ADMIN được sửa; kiểm quyền ở service qua GenealogyAccess.
 */
@Service
public class RelationshipService {

    private final ParentChildRepository parentChildRepository;
    private final MarriageRepository marriageRepository;
    private final PersonRepository personRepository;
    private final GenealogyAccess access;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public RelationshipService(ParentChildRepository parentChildRepository, MarriageRepository marriageRepository,
                               PersonRepository personRepository, GenealogyAccess access,
                               AuditLogger auditLogger, Clock clock) {
        this.parentChildRepository = parentChildRepository;
        this.marriageRepository = marriageRepository;
        this.personRepository = personRepository;
        this.access = access;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public ParentChildData addParent(UUID childId, ParentInput input) {
        Person child = find(childId);
        UUID familyId = child.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, child.getBranchId());
        if (childId.equals(input.parentId())) {
            throw new ApiException(ErrorCode.GEN_008); // BR-GEN-05: trùng người
        }
        Person parent = find(input.parentId());
        if (!familyId.equals(parent.getFamilyId())) {
            throw new ApiException(ErrorCode.PERM_001); // UC-GEN-04 tiền điều kiện: cùng gia đình
        }
        ParentKind kind = input.kind() == null ? ParentKind.BIOLOGICAL : input.kind();
        if (parentChildRepository.existsByParentIdAndChildIdAndDeletedAtIsNull(parent.getId(), childId)) {
            throw new ApiException(ErrorCode.GEN_008);
        }
        if (kind == ParentKind.BIOLOGICAL && parentChildRepository.countByChildIdAndParentRoleAndKindAndDeletedAtIsNull(
                childId, input.parentRole().name(), ParentKind.BIOLOGICAL.name()) > 0) {
            throw new ApiException(ErrorCode.GEN_005); // BR-GEN-02: 1 cha ruột, 1 mẹ ruột
        }
        // BR-GEN-03: con đã là tổ tiên của người định làm cha/mẹ thì thêm cạnh này tạo vòng lặp.
        if (parentChildRepository.isAncestorOf(parent.getId(), childId)) {
            throw new ApiException(ErrorCode.GEN_006);
        }
        if (bornNotBefore(parent, child)) {
            throw new ApiException(ErrorCode.GEN_007); // BR-GEN-04
        }
        ParentChild relation = new ParentChild();
        relation.setParentId(parent.getId());
        relation.setChildId(childId);
        relation.setParentRole(input.parentRole().name());
        relation.setKind(kind.name());
        relation.setCreatedBy(viewer.userId());
        relation.setUpdatedBy(viewer.userId());
        ParentChild saved = parentChildRepository.saveAndFlush(relation);
        auditLogger.log("PARENT_CHILD_CREATED", "PARENT_CHILD", saved.getId(), familyId, Map.of());
        return toData(saved);
    }

    @Transactional
    public void removeParent(UUID childId, UUID parentId) {
        Person child = find(childId);
        UUID familyId = child.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, child.getBranchId());
        ParentChild relation = parentChildRepository.findByParentIdAndChildIdAndDeletedAtIsNull(parentId, childId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        relation.setDeletedAt(Instant.now(clock));
        relation.setUpdatedBy(viewer.userId());
        auditLogger.log("PARENT_CHILD_DELETED", "PARENT_CHILD", relation.getId(), familyId, Map.of());
    }

    @Transactional
    public MarriageData addMarriage(MarriageInput input) {
        Person first = find(input.person1Id());
        UUID familyId = first.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        if (input.person1Id().equals(input.person2Id())) {
            throw ApiException.invalidField("person2Id", "Hai người phải khác nhau");
        }
        Person second = find(input.person2Id());
        if (!familyId.equals(second.getFamilyId())) {
            throw new ApiException(ErrorCode.PERM_001); // UC-GEN-05 tiền điều kiện: cùng gia đình
        }
        requireManageEither(viewer, first, second);
        if (input.startDate() != null && input.startDate().isAfter(LocalDate.now(clock))) {
            throw ApiException.invalidField("startDate", "Ngày kết hôn không được sau hôm nay");
        }
        if (first.isDeceased() || second.isDeceased()) {
            throw new ApiException(ErrorCode.GEN_011); // BR-GEN-08
        }
        if (!marriageRepository.findMarriedByPersonId(first.getId()).isEmpty()
                || !marriageRepository.findMarriedByPersonId(second.getId()).isEmpty()) {
            throw new ApiException(ErrorCode.GEN_009); // BR-GEN-07: tái hôn khi hôn nhân trước DIVORCED/WIDOWED
        }
        if (areRelatives(first.getId(), second.getId())) {
            throw new ApiException(ErrorCode.GEN_010);
        }
        Marriage marriage = new Marriage();
        marriage.setPerson1Id(first.getId());
        marriage.setPerson2Id(second.getId());
        marriage.setStatus(MarriageStatus.MARRIED.name());
        marriage.setStartDate(input.startDate());
        marriage.setCreatedBy(viewer.userId());
        marriage.setUpdatedBy(viewer.userId());
        Marriage saved = marriageRepository.saveAndFlush(marriage);
        auditLogger.log("MARRIAGE_CREATED", "MARRIAGE", saved.getId(), familyId, Map.of());
        return toData(saved);
    }

    /** Ly hôn (DIVORCED kèm ngày kết thúc), góa, hoặc sửa nhập nhầm về MARRIED (UC-GEN-05). */
    @Transactional
    public MarriageData updateMarriage(UUID marriageId, MarriageUpdate input) {
        Marriage marriage = findMarriage(marriageId);
        Person first = find(marriage.getPerson1Id());
        Person second = find(marriage.getPerson2Id());
        Viewer viewer = access.requireRead(first.getFamilyId());
        requireManageEither(viewer, first, second);
        LocalDate startDate = input.startDate() != null ? input.startDate() : marriage.getStartDate();
        LocalDate endDate = input.status() == MarriageStatus.MARRIED ? null : input.endDate();
        if (input.status() == MarriageStatus.DIVORCED && endDate == null) {
            throw ApiException.invalidField("endDate", "Nhập ngày ly hôn");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw ApiException.invalidField("endDate", "Ngày kết thúc không được trước ngày kết hôn");
        }
        if (input.status() == MarriageStatus.MARRIED && !MarriageStatus.MARRIED.name().equals(marriage.getStatus())) {
            if (first.isDeceased() || second.isDeceased()) {
                throw new ApiException(ErrorCode.GEN_011);
            }
            if (marriedElsewhere(first.getId(), marriageId) || marriedElsewhere(second.getId(), marriageId)) {
                throw new ApiException(ErrorCode.GEN_009);
            }
        }
        marriage.setStatus(input.status().name());
        marriage.setStartDate(startDate);
        marriage.setEndDate(endDate);
        marriage.setUpdatedBy(viewer.userId());
        Marriage saved = marriageRepository.saveAndFlush(marriage);
        auditLogger.log("MARRIAGE_UPDATED", "MARRIAGE", marriageId, first.getFamilyId(), Map.of());
        return toData(saved);
    }

    @Transactional
    public void deleteMarriage(UUID marriageId) {
        Marriage marriage = findMarriage(marriageId);
        Person first = find(marriage.getPerson1Id());
        Person second = find(marriage.getPerson2Id());
        Viewer viewer = access.requireRead(first.getFamilyId());
        requireManageEither(viewer, first, second);
        marriage.setDeletedAt(Instant.now(clock));
        marriage.setUpdatedBy(viewer.userId());
        auditLogger.log("MARRIAGE_DELETED", "MARRIAGE", marriageId, first.getFamilyId(), Map.of());
    }

    /** BR-GEN-07: trực hệ (một người là tổ tiên của người kia) hoặc anh chị em ruột. */
    private boolean areRelatives(UUID a, UUID b) {
        return parentChildRepository.isAncestorOf(a, b) || parentChildRepository.isAncestorOf(b, a)
                || parentChildRepository.areSiblings(a, b);
    }

    private boolean marriedElsewhere(UUID personId, UUID marriageId) {
        return marriageRepository.findMarriedByPersonId(personId).stream().anyMatch(m -> !m.getId().equals(marriageId));
    }

    /** Hôn nhân nối hai người có thể ở hai chi; người quản lý một trong hai chi được thao tác (BR-GEN-14). */
    private void requireManageEither(Viewer viewer, Person first, Person second) {
        if (!access.canManage(viewer, first.getFamilyId(), first.getBranchId())
                && !access.canManage(viewer, second.getFamilyId(), second.getBranchId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    /** BR-GEN-04: dùng ngày sinh; thiếu thì so năm sinh (người đã mất chỉ cần năm sinh, BR-GEN-17). */
    private static boolean bornNotBefore(Person parent, Person child) {
        if (parent.getBirthDate() != null && child.getBirthDate() != null) {
            return !parent.getBirthDate().isBefore(child.getBirthDate());
        }
        Integer parentYear = year(parent);
        Integer childYear = year(child);
        return parentYear != null && childYear != null && parentYear > childYear;
    }

    private static Integer year(Person p) {
        if (p.getBirthDate() != null) {
            return p.getBirthDate().getYear();
        }
        return p.getBirthYear() == null ? null : p.getBirthYear().intValue();
    }

    private Person find(UUID personId) {
        return personRepository.findByIdAndDeletedAtIsNull(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private Marriage findMarriage(UUID marriageId) {
        return marriageRepository.findByIdAndDeletedAtIsNull(marriageId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private static OffsetDateTime at(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static ParentChildData toData(ParentChild r) {
        return new ParentChildData(r.getParentId(), r.getChildId(), r.getParentRole(), r.getKind(),
                at(r.getCreatedAt()), at(r.getUpdatedAt()));
    }

    private static MarriageData toData(Marriage m) {
        return new MarriageData(m.getId(), m.getPerson1Id(), m.getPerson2Id(), m.getStatus(), m.getStartDate(),
                m.getEndDate(), at(m.getCreatedAt()), at(m.getUpdatedAt()));
    }
}
