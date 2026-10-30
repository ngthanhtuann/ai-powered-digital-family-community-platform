package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.genealogy.dto.MarriageInput;
import com.familyconnect.modules.genealogy.dto.MarriageStatusInput;
import com.familyconnect.modules.genealogy.dto.ParentChildInput;
import com.familyconnect.modules.genealogy.entity.Marriage;
import com.familyconnect.modules.genealogy.entity.ParentChild;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.MarriageRepository;
import com.familyconnect.modules.genealogy.repository.ParentChildRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RelationshipService {

    private final ParentChildRepository parentChildRepository;
    private final MarriageRepository marriageRepository;
    private final PersonRepository personRepository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public RelationshipService(ParentChildRepository parentChildRepository,
                               MarriageRepository marriageRepository,
                               PersonRepository personRepository,
                               AuditLogger auditLogger,
                               Clock clock) {
        this.parentChildRepository = parentChildRepository;
        this.marriageRepository = marriageRepository;
        this.personRepository = personRepository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public ParentChild addParent(UUID childId, ParentChildInput input, UUID actorUserId) {
        if (childId.equals(input.getParentId())) {
            throw ApiException.invalidField("parentId", "Không thể thêm chính mình làm cha/mẹ");
        }

        Person child = personRepository.findByIdAndDeletedAtIsNull(childId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        Person parent = personRepository.findByIdAndDeletedAtIsNull(input.getParentId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));

        if (!child.getFamilyId().equals(parent.getFamilyId())) {
            throw ApiException.invalidField("parentId", "Cha/mẹ và con phải thuộc cùng một gia đình");
        }

        if ("BIOLOGICAL".equalsIgnoreCase(input.getKind())) {
            long count = parentChildRepository.countByChildIdAndParentRoleAndKindAndDeletedAtIsNull(
                    childId, input.getParentRole(), "BIOLOGICAL");
            if (count >= 1) {
                throw new ApiException(ErrorCode.CONFLICT_001, "Người này đã có đủ " + input.getParentRole().toLowerCase() + " ruột");
            }
        }

        if (parentChildRepository.existsByParentIdAndChildIdAndDeletedAtIsNull(input.getParentId(), childId)) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Quan hệ cha-mẹ-con này đã tồn tại");
        }

        if (parentChildRepository.isAncestorOf(childId, input.getParentId())) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Không thể thêm quan hệ vì tạo vòng lặp tổ tiên");
        }

        if (parent.getBirthDate() != null && child.getBirthDate() != null) {
            if (!parent.getBirthDate().isBefore(child.getBirthDate())) {
                throw ApiException.invalidField("birthDate", "Ngày sinh của cha/mẹ phải trước ngày sinh của con");
            }
        }

        ParentChild relation = new ParentChild();
        relation.setParentId(input.getParentId());
        relation.setChildId(childId);
        relation.setParentRole(input.getParentRole().toUpperCase());
        relation.setKind(input.getKind() != null ? input.getKind().toUpperCase() : "BIOLOGICAL");
        relation.setCreatedBy(actorUserId);
        relation.setUpdatedBy(actorUserId);

        ParentChild saved = parentChildRepository.save(relation);
        auditLogger.log("PARENT_CHILD_CREATED", "PARENT_CHILD", saved.getId(), child.getFamilyId(), Map.of());
        return saved;
    }

    @Transactional
    public void removeParentChild(UUID id, UUID actorUserId) {
        ParentChild relation = parentChildRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        relation.setDeletedAt(Instant.now(clock));
        relation.setUpdatedBy(actorUserId);
        parentChildRepository.save(relation);
        auditLogger.log("PARENT_CHILD_DELETED", "PARENT_CHILD", id, null, Map.of());
    }

    @Transactional
    public Marriage addMarriage(MarriageInput input, UUID actorUserId) {
        if (input.getPerson1Id().equals(input.getPerson2Id())) {
            throw ApiException.invalidField("person2Id", "Không thể kết hôn với chính mình");
        }

        Person p1 = personRepository.findByIdAndDeletedAtIsNull(input.getPerson1Id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        Person p2 = personRepository.findByIdAndDeletedAtIsNull(input.getPerson2Id())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));

        if (!p1.getFamilyId().equals(p2.getFamilyId())) {
            throw ApiException.invalidField("person2Id", "Hai người phải thuộc cùng một gia đình");
        }

        if (p1.isDeceased() || p2.isDeceased()) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Không thể thiết lập hôn nhân khi một trong hai người đã qua đời");
        }

        if (marriageRepository.findActiveMarriageByPersonId(input.getPerson1Id()).isPresent() ||
            marriageRepository.findActiveMarriageByPersonId(input.getPerson2Id()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Một trong hai người đang có hôn nhân hợp pháp (MARRIED)");
        }

        if (marriageRepository.findBetweenPersons(input.getPerson1Id(), input.getPerson2Id()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Quan hệ hôn nhân giữa hai người đã tồn tại");
        }

        Marriage marriage = new Marriage();
        marriage.setPerson1Id(input.getPerson1Id());
        marriage.setPerson2Id(input.getPerson2Id());
        marriage.setStatus("MARRIED");
        marriage.setStartDate(input.getStartDate());
        marriage.setCreatedBy(actorUserId);
        marriage.setUpdatedBy(actorUserId);

        Marriage saved = marriageRepository.save(marriage);
        auditLogger.log("MARRIAGE_CREATED", "MARRIAGE", saved.getId(), p1.getFamilyId(), Map.of());
        return saved;
    }

    @Transactional
    public Marriage updateMarriageStatus(UUID id, MarriageStatusInput input, UUID actorUserId) {
        Marriage marriage = marriageRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));

        marriage.setStatus(input.getStatus().toUpperCase());
        if (input.getEndDate() != null) {
            marriage.setEndDate(input.getEndDate());
        }
        marriage.setUpdatedBy(actorUserId);
        Marriage saved = marriageRepository.save(marriage);
        auditLogger.log("MARRIAGE_UPDATED", "MARRIAGE", id, null, Map.of());
        return saved;
    }

    @Transactional
    public void removeMarriage(UUID id, UUID actorUserId) {
        Marriage marriage = marriageRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        marriage.setDeletedAt(Instant.now(clock));
        marriage.setUpdatedBy(actorUserId);
        marriageRepository.save(marriage);
        auditLogger.log("MARRIAGE_DELETED", "MARRIAGE", id, null, Map.of());
    }
}
