package com.familyconnect.modules.heritage;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;

import java.util.HashSet;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class StubGenealogyQuery implements GenealogyQuery {

    private final Set<PersonFamily> people = new HashSet<>();

    public void add(UUID personId, UUID familyId) {
        people.add(new PersonFamily(personId, familyId));
    }

    @Override
    public Optional<UUID> findPersonFamilyId(UUID personId) {
        return people.stream()
                .filter(person -> person.personId().equals(personId))
                .map(PersonFamily::familyId)
                .findFirst();
    }

    @Override
    public Optional<DirectoryPerson> findDirectoryPerson(UUID personId) {
        return Optional.empty();
    }

    @Override
    public Optional<DirectoryPerson> findDirectoryPersonByUserId(UUID userId) {
        return Optional.empty();
    }

    @Override
    public boolean isParentOf(UUID parentPersonId, UUID childPersonId) {
        return false;
    }

    @Override
    public boolean isBranchWithin(UUID familyId, UUID branchId, UUID ancestorBranchId) {
        return false;
    }

    @Override
    public DirectoryPage searchDirectoryPeople(UUID familyId, String location, Integer generation, LocalDate asOfDate,
                                               Set<UUID> personIds, boolean excludeChildren,
                                               String sortField, boolean ascending, int page, int size) {
        return new DirectoryPage(List.of(), 0);
    }

    private record PersonFamily(UUID personId, UUID familyId) {
    }
}
