package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.DashboardDemographicsRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.MarriageRepository;
import com.familyconnect.modules.genealogy.repository.ParentChildRepository;
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

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private DashboardDemographicsRepository dashboardRepository;
    @Mock
    private KinshipService kinshipService;

    @Mock
    private ParentChildRepository parentChildRepository;

    @Mock
    private MarriageRepository marriageRepository;

    private GenealogyQueryImpl genealogyQuery;

    @BeforeEach
    void setUp() {
        genealogyQuery = new GenealogyQueryImpl(personRepository, branchRepository,
                familyRepository, dashboardRepository, kinshipService, parentChildRepository, marriageRepository);
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

    @Test
    void relationshipLabels_LoadsFamilyOnceAndSkipsPeopleOutsideFamily() {
        UUID familyId = UUID.randomUUID();
        Person father = person(familyId, "MALE");
        Person child = person(familyId, "MALE");
        UUID outsider = UUID.randomUUID();
        com.familyconnect.modules.genealogy.entity.ParentChild link =
                new com.familyconnect.modules.genealogy.entity.ParentChild();
        link.setParentId(father.getId());
        link.setChildId(child.getId());
        link.setParentRole("FATHER");
        link.setKind("BIOLOGICAL");
        when(personRepository.findByIdAndDeletedAtIsNull(child.getId())).thenReturn(Optional.of(child));
        when(personRepository.findAllByFamilyIdAndDeletedAtIsNull(familyId)).thenReturn(java.util.List.of(father, child));
        when(parentChildRepository.findByChildIdAndDeletedAtIsNull(any())).thenAnswer(call ->
                child.getId().equals(call.getArgument(0)) ? java.util.List.of(link) : java.util.List.of());

        var labels = genealogyQuery.relationshipLabels(child.getId(), java.util.List.of(father.getId(), outsider));

        assertEquals(java.util.Map.of(father.getId(), "cha"), labels);
        verify(personRepository).findAllByFamilyIdAndDeletedAtIsNull(familyId);
        verify(personRepository, never()).findByIdAndDeletedAtIsNull(father.getId());
    }

    private static Person person(UUID familyId, String gender) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setFamilyId(familyId);
        person.setFullName("Người thử");
        person.setGender(gender);
        return person;
    }
}
