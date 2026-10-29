package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
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

    public GenealogyQueryImpl(PersonRepository personRepository, BranchRepository branchRepository) {
        this.personRepository = personRepository;
        this.branchRepository = branchRepository;
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
