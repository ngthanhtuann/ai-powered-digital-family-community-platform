package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DashboardDemographics;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import com.familyconnect.modules.genealogy.repository.DashboardDemographicsRepository;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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

    public GenealogyQueryImpl(PersonRepository personRepository, BranchRepository branchRepository,
                              FamilyRepository familyRepository,
                              DashboardDemographicsRepository dashboardRepository, KinshipService kinshipService) {
        this.personRepository = personRepository;
        this.branchRepository = branchRepository;
        this.familyRepository = familyRepository;
        this.dashboardRepository = dashboardRepository;
        this.kinshipService = kinshipService;
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
    public DirectoryPage searchDirectoryPeople(UUID familyId, String location, Integer generation, LocalDate asOfDate,
                                               Set<UUID> personIds, boolean excludeChildren,
                                               String sortField, boolean ascending,
                                               int page, int size) {
        DirectoryPage result = personRepository.search(familyId, location, generation, asOfDate, personIds,
                excludeChildren, sortField, ascending, page, size);
        return new DirectoryPage(result.people(), result.total());
    }

    private static DirectoryPerson toDirectoryPerson(Person person) {
        Integer birthYear = person.getBirthDate() == null
                ? (person.getBirthYear() == null ? null : person.getBirthYear().intValue())
                : person.getBirthDate().getYear();
        return new DirectoryPerson(person.getId(), person.getFamilyId(), person.getBranchId(),
                person.getFullName(), person.getBirthDate(), birthYear, person.getCurrentProvince(),
                person.isDeceased(), person.getUserId(), null);
    }
}
