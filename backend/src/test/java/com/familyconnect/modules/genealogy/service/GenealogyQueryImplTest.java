package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenealogyQueryImplTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private BranchRepository branchRepository;

    private GenealogyQueryImpl genealogyQuery;

    @BeforeEach
    void setUp() {
        genealogyQuery = new GenealogyQueryImpl(personRepository, branchRepository);
    }

    @Test
    void findPersonFamilyId_WhenPersonExistsAndNotDeleted_ShouldReturnFamilyId() {
        UUID personId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        Person person = new Person();
        person.setId(personId);
        person.setFamilyId(familyId);
        person.setDeletedAt(null);

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        Optional<UUID> result = genealogyQuery.findPersonFamilyId(personId);

        assertTrue(result.isPresent());
        assertEquals(familyId, result.get());
    }

    @Test
    void findPersonFamilyId_WhenPersonIsDeleted_ShouldReturnEmpty() {
        UUID personId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        Person person = new Person();
        person.setId(personId);
        person.setFamilyId(familyId);
        person.setDeletedAt(OffsetDateTime.now());

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        Optional<UUID> result = genealogyQuery.findPersonFamilyId(personId);

        assertTrue(result.isEmpty());
    }
}
