package com.familyconnect.modules.heritage;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;

import java.util.HashSet;
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

    private record PersonFamily(UUID personId, UUID familyId) {
    }
}
