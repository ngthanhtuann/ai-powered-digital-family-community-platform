package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.community.api.CommunityQuery;
import com.familyconnect.modules.directory.api.DirectoryQuery;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Chunk PERSON cho AI (UC-AI-01, BR-AI-07, BR-AI-13, BR-AI-14): nghề nghiệp từ Directory, phạm vi toàn gia đình. */
class PersonServiceSearchIndexTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    private PersonRepository persons;
    private DirectoryQuery directory;
    private AiGateway ai;
    private PersonService service;

    @BeforeEach
    void setUp() {
        persons = mock(PersonRepository.class);
        directory = mock(DirectoryQuery.class);
        ai = mock(AiGateway.class);
        service = new PersonService(persons, mock(FamilyRepository.class), mock(BranchRepository.class),
                mock(GenealogyAccess.class), mock(MembershipQuery.class), mock(AuditLogger.class), ai,
                mock(CommunityQuery.class), directory, CLOCK);
    }

    private Person person(String name, LocalDate birthDate) {
        Person person = new Person();
        person.setId(UUID.randomUUID());
        person.setFamilyId(FAMILY_ID);
        person.setBranchId(BRANCH_ID);
        person.setFullName(name);
        person.setBirthDate(birthDate);
        person.setCurrentProvince("Hà Nội");
        person.setCurrentAddress("12 Phố Huế");
        when(persons.findByIdAndDeletedAtIsNull(person.getId())).thenReturn(Optional.of(person));
        return person;
    }

    @Test
    void adultChunkHasJobTitleProvinceAndFamilyScopeButNoAddress() {
        Person doctor = person("Trần Minh An", LocalDate.of(1985, 3, 2));
        when(directory.findJobTitle(doctor.getId())).thenReturn(Optional.of("Bác sĩ"));

        service.refresh(doctor.getId());

        // branchId null dù người thuộc chi: hồ sơ cả gia đình được xem.
        verify(ai).upsertSource(eq(AiGateway.SourceType.PERSON), eq(doctor.getId()), eq(FAMILY_ID), isNull(),
                eq("Họ tên: Trần Minh An. Năm sinh: 1985. Tỉnh/thành: Hà Nội. Nghề nghiệp: Bác sĩ."));
    }

    @Test
    void childChunkHasOnlyName() {
        Person child = person("Bé Na", LocalDate.of(2018, 5, 1));

        service.refresh(child.getId());

        verify(ai).upsertSource(eq(AiGateway.SourceType.PERSON), eq(child.getId()), eq(FAMILY_ID), isNull(),
                eq("Họ tên: Bé Na."));
        verify(directory, never()).findJobTitle(any());
    }

    @Test
    void missingPersonIsIgnored() {
        UUID missing = UUID.randomUUID();
        when(persons.findByIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty());

        service.refresh(missing);

        verify(ai, never()).upsertSource(any(), any(), any(), any(), anyString());
    }
}
