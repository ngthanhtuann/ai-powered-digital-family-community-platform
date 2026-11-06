package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse.PathPerson;
import com.familyconnect.modules.genealogy.entity.Marriage;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.MarriageRepository;
import com.familyconnect.modules.genealogy.repository.ParentChildRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** UC-GEN-08 GET /relationships: kiểm quyền rồi chạy {@link KinshipCalculator} trên dữ liệu bảng gia phả. */
@Service
public class KinshipService {

    private final PersonRepository personRepository;
    private final ParentChildRepository parentChildRepository;
    private final MarriageRepository marriageRepository;
    private final GenealogyAccess access;

    public KinshipService(PersonRepository personRepository, ParentChildRepository parentChildRepository,
                          MarriageRepository marriageRepository, GenealogyAccess access) {
        this.personRepository = personRepository;
        this.parentChildRepository = parentChildRepository;
        this.marriageRepository = marriageRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public RelationshipResponse relationship(UUID personAId, UUID personBId) {
        Person a = find(personAId);
        Person b = find(personBId);
        access.requireRead(a.getFamilyId()); // PERM_002 chưa xác minh, PERM_001 gia đình khác
        if (!a.getFamilyId().equals(b.getFamilyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        DbGraph graph = new DbGraph();
        KinshipCalculator.Result r = new KinshipCalculator(graph).relationship(a.getId().toString(), b.getId().toString());
        List<PathPerson> path = r.path().stream()
                .map(id -> new PathPerson(UUID.fromString(id), graph.person(id).fullName()))
                .toList();
        return new RelationshipResponse(r.supported(), r.relation(), describe(r, a, b), r.commonAncestorId() == null
                ? null : UUID.fromString(r.commonAncestorId()), r.stepsFromA(), r.stepsFromB(), path);
    }

    private static String describe(KinshipCalculator.Result r, Person a, Person b) {
        if (!r.supported()) {
            return "Chưa hỗ trợ hoặc không có quan hệ huyết thống trong dữ liệu";
        }
        if (a.getId().equals(b.getId())) {
            return a.getFullName() + " là cùng một người";
        }
        return a.getFullName() + " là " + r.relation() + " của " + b.getFullName();
    }

    private Person find(UUID id) {
        return personRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    /** Đọc bảng person, parent_child, marriage; nhớ đệm người đã đọc trong một yêu cầu. */
    private final class DbGraph implements KinshipCalculator.Graph {
        private final Map<String, KinshipCalculator.Person> people = new HashMap<>();

        @Override
        public KinshipCalculator.Person person(String id) {
            return people.computeIfAbsent(id, key -> {
                Person p = find(UUID.fromString(key));
                return new KinshipCalculator.Person(key, p.getFullName(), p.getGender(), p.getBirthDate(),
                        p.getBirthYear() == null ? null : p.getBirthYear().intValue());
            });
        }

        @Override
        public List<KinshipCalculator.ParentLink> parentsOf(String id) {
            return parentChildRepository.findByChildIdAndDeletedAtIsNull(UUID.fromString(id)).stream()
                    .map(pc -> new KinshipCalculator.ParentLink(pc.getParentId().toString(), pc.getParentRole(), pc.getKind()))
                    .toList();
        }

        @Override
        public List<KinshipCalculator.MarriageLink> marriagesOf(String id) {
            UUID self = UUID.fromString(id);
            return marriageRepository.findAllByPersonId(self).stream()
                    .map(m -> new KinshipCalculator.MarriageLink(spouse(m, self).toString(), m.getStatus()))
                    .toList();
        }

        private static UUID spouse(Marriage m, UUID self) {
            return self.equals(m.getPerson1Id()) ? m.getPerson2Id() : m.getPerson1Id();
        }
    }
}
