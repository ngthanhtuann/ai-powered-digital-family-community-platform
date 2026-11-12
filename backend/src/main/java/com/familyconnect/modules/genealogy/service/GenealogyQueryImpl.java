package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DashboardDemographics;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import com.familyconnect.modules.genealogy.entity.Marriage;
import com.familyconnect.modules.genealogy.repository.DashboardDemographicsRepository;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.MarriageRepository;
import com.familyconnect.modules.genealogy.repository.ParentChildRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GenealogyQueryImpl implements GenealogyQuery {

    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final FamilyRepository familyRepository;
    private final DashboardDemographicsRepository dashboardRepository;
    private final KinshipService kinshipService;
    private final ParentChildRepository parentChildRepository;
    private final MarriageRepository marriageRepository;

    public GenealogyQueryImpl(PersonRepository personRepository, BranchRepository branchRepository,
                              FamilyRepository familyRepository,
                              DashboardDemographicsRepository dashboardRepository, KinshipService kinshipService,
                              ParentChildRepository parentChildRepository,
                              MarriageRepository marriageRepository) {
        this.personRepository = personRepository;
        this.branchRepository = branchRepository;
        this.familyRepository = familyRepository;
        this.dashboardRepository = dashboardRepository;
        this.kinshipService = kinshipService;
        this.parentChildRepository = parentChildRepository;
        this.marriageRepository = marriageRepository;
    }

    @Override
    public RelationshipResult relationship(UUID personAId, UUID personBId) {
        RelationshipResponse r = kinshipService.relationship(personAId, personBId);
        return new RelationshipResult(r.supported(), r.relation(), r.stepsFromA(), r.stepsFromB(),
                r.path().stream().map(p -> new PathStep(p.id(), p.fullName())).toList());
    }

    @Override
    public Optional<UUID> findPersonFamilyId(UUID personId) {
        return personRepository.findById(personId)
                .filter(person -> person.getDeletedAt() == null)
                .map(person -> person.getFamilyId());
    }

    @Override
    public Optional<DirectoryPerson> findDirectoryPerson(UUID personId) {
        return personRepository.findByIdAndDeletedAtIsNull(personId).map(GenealogyQueryImpl::toDirectoryPerson);
    }

    @Override
    public Optional<DirectoryPerson> findDirectoryPersonByUserId(UUID userId) {
        return personRepository.findByUserIdAndDeletedAtIsNull(userId).map(GenealogyQueryImpl::toDirectoryPerson);
    }

    @Override
    public boolean isParentOf(UUID parentPersonId, UUID childPersonId) {
        return personRepository.isActiveParentOf(parentPersonId, childPersonId);
    }

    @Override
    public List<UUID> findMinorPersonIds(UUID familyId, LocalDate asOfDate) {
        return personRepository.findMinorIds(familyId, asOfDate);
    }

    @Override
    public boolean isBranchWithin(UUID familyId, UUID branchId, UUID ancestorBranchId) {
        return branchRepository.isWithin(familyId, branchId, ancestorBranchId);
    }

    @Override
    public boolean familyExists(UUID familyId) {
        return familyRepository.findByIdAndDeletedAtIsNull(familyId).isPresent();
    }

    @Override
    public DashboardDemographics getDashboardDemographics(UUID familyId, UUID branchId, LocalDate asOfDate) {
        return dashboardRepository.get(familyId, branchId, asOfDate);
    }

    @Override
    public DirectoryPage searchDirectoryPeople(UUID familyId, String keyword, String location,
                                               Integer generation, LocalDate asOfDate,
                                               Set<UUID> personIds, boolean excludeChildren,
                                               String sortField, boolean ascending,
                                               int page, int size) {
        DirectoryPage result = personRepository.search(familyId, keyword, location, generation, asOfDate, personIds,
                excludeChildren, sortField, ascending, page, size);
        return new DirectoryPage(result.people(), result.total());
    }

    @Override
    public Map<UUID, String> relationshipLabels(UUID referencePersonId, List<UUID> personIds) {
        Person reference = personRepository.findByIdAndDeletedAtIsNull(referencePersonId).orElse(null);
        if (reference == null || personIds.isEmpty()) {
            return Map.of();
        }

        // Nạp cả gia đình một lần thay vì truy vấn từng người (trước đây 2 truy vấn mỗi người khi xuất CSV).
        CachedGraph graph = new CachedGraph(reference.getFamilyId());
        personRepository.findAllByFamilyIdAndDeletedAtIsNull(reference.getFamilyId())
                .forEach(person -> graph.people.put(person.getId(), toKinshipPerson(person)));
        KinshipCalculator calculator = new KinshipCalculator(graph);
        Map<UUID, String> labels = new HashMap<>();
        for (UUID personId : personIds) {
            if (!graph.people.containsKey(personId)) {
                continue; // đã xóa hoặc không cùng gia đình
            }
            KinshipCalculator.Result result = calculator.relationship(personId.toString(), referencePersonId.toString());
            if (result.supported()) {
                labels.put(personId, result.relation());
            }
        }
        return Map.copyOf(labels);
    }

    private static DirectoryPerson toDirectoryPerson(Person person) {
        Integer birthYear = person.getBirthDate() == null
                ? (person.getBirthYear() == null ? null : person.getBirthYear().intValue())
                : person.getBirthDate().getYear();
        return new DirectoryPerson(person.getId(), person.getFamilyId(), person.getBranchId(),
                person.getFullName(), person.getBirthDate(), birthYear, person.getCurrentProvince(),
                person.isDeceased(), person.getUserId(), person.getGender(), null);
    }

    private static KinshipCalculator.Person toKinshipPerson(Person person) {
        return new KinshipCalculator.Person(person.getId().toString(), person.getFullName(), person.getGender(),
                person.getBirthDate(), person.getBirthYear() == null ? null : person.getBirthYear().intValue());
    }

    private final class CachedGraph implements KinshipCalculator.Graph {

        private final UUID familyId;
        private final Map<UUID, KinshipCalculator.Person> people = new HashMap<>();
        private final Map<UUID, List<KinshipCalculator.ParentLink>> parents = new HashMap<>();
        private final Map<UUID, List<KinshipCalculator.MarriageLink>> marriages = new HashMap<>();

        private CachedGraph(UUID familyId) {
            this.familyId = familyId;
        }

        @Override
        public KinshipCalculator.Person person(String id) {
            UUID personId = UUID.fromString(id);
            return people.computeIfAbsent(personId, key -> toKinshipPerson(personRepository.findByIdAndDeletedAtIsNull(key)
                    .filter(value -> familyId.equals(value.getFamilyId()))
                    .orElseThrow(() -> new IllegalStateException("Quan hệ gia phả tham chiếu thành viên không hợp lệ"))));
        }

        @Override
        public List<KinshipCalculator.ParentLink> parentsOf(String id) {
            UUID personId = UUID.fromString(id);
            return parents.computeIfAbsent(personId, key -> parentChildRepository
                    .findByChildIdAndDeletedAtIsNull(key).stream()
                    .map(link -> new KinshipCalculator.ParentLink(link.getParentId().toString(),
                            link.getParentRole(), link.getKind()))
                    .toList());
        }

        @Override
        public List<KinshipCalculator.MarriageLink> marriagesOf(String id) {
            UUID personId = UUID.fromString(id);
            return marriages.computeIfAbsent(personId, key -> marriageRepository.findAllByPersonId(key).stream()
                    .map(marriage -> new KinshipCalculator.MarriageLink(
                            spouseId(marriage, key).toString(), marriage.getStatus()))
                    .toList());
        }

        private UUID spouseId(Marriage marriage, UUID personId) {
            return personId.equals(marriage.getPerson1Id()) ? marriage.getPerson2Id() : marriage.getPerson1Id();
        }
    }
}
