package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GenealogyQueryImpl implements GenealogyQuery {

    private final PersonRepository personRepository;

    public GenealogyQueryImpl(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public Optional<UUID> findPersonFamilyId(UUID personId) {
        return personRepository.findById(personId)
                .filter(person -> person.getDeletedAt() == null)
                .map(person -> person.getFamilyId());
    }
}
