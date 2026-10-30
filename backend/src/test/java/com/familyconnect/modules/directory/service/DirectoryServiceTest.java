package com.familyconnect.modules.directory.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.directory.dto.DirectoryRequests.EducationProfileRequest;
import com.familyconnect.modules.directory.dto.DirectoryRequests.ProfessionProfileRequest;
import com.familyconnect.modules.directory.entity.EducationProfile;
import com.familyconnect.modules.directory.entity.ProfessionProfile;
import com.familyconnect.modules.directory.repository.EducationProfileRepository;
import com.familyconnect.modules.directory.repository.ProfessionProfileRepository;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CHILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ProfessionProfileRepository professionRepository;
    @Mock
    private EducationProfileRepository educationRepository;
    @Mock
    private GenealogyQuery genealogyQuery;
    @Mock
    private MembershipQuery membershipQuery;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private com.familyconnect.modules.genealogy.api.PersonSearchIndex personSearchIndex;

    private DirectoryService service;

    @BeforeEach
    void setUp() {
        service = new DirectoryService(professionRepository, educationRepository, genealogyQuery,
                membershipQuery, auditLogger, personSearchIndex, CLOCK);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(ACTOR_ID, List.of("MEMBER")), null, List.of()));
        lenient().when(membershipQuery.getMembership(ACTOR_ID)).thenReturn(
                new MembershipView(ACTOR_ID, FAMILY_ID, null, EffectiveRole.MEMBER, true));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfessionProfileCreatesProfileForOwnPerson() {
        DirectoryPerson person = person(PERSON_ID, ACTOR_ID, LocalDate.of(1990, 1, 1), false);
        when(genealogyQuery.findDirectoryPerson(PERSON_ID)).thenReturn(Optional.of(person));
        when(genealogyQuery.findDirectoryPersonByUserId(ACTOR_ID)).thenReturn(Optional.of(person));
        when(professionRepository.findByPersonIdAndDeletedAtIsNull(PERSON_ID)).thenReturn(Optional.empty());
        when(professionRepository.saveAndFlush(any(ProfessionProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateProfessionProfile(PERSON_ID,
                new ProfessionProfileRequest("  Kỹ sư  ", " ABC ", " Đà Nẵng "));

        assertTrue(result.created());
        assertEquals("Kỹ sư", result.data().jobTitle());
        ArgumentCaptor<ProfessionProfile> saved = ArgumentCaptor.forClass(ProfessionProfile.class);
        verify(professionRepository).saveAndFlush(saved.capture());
        assertEquals("ABC", saved.getValue().getCompany());
        assertEquals("Đà Nẵng", saved.getValue().getWorkLocation());
        verify(auditLogger).log(eq("PROFESSION_PROFILE_CREATED"), eq("PROFESSION_PROFILE"),
                any(), eq(FAMILY_ID), anyMap());
        verify(personSearchIndex).refresh(PERSON_ID);
    }

    @Test
    void memberCannotEditAnotherAdultProfile() {
        UUID otherPersonId = UUID.randomUUID();
        DirectoryPerson target = person(otherPersonId, null, LocalDate.of(1990, 1, 1), false);
        DirectoryPerson actorPerson = person(PERSON_ID, ACTOR_ID, LocalDate.of(1990, 1, 1), false);
        when(genealogyQuery.findDirectoryPerson(otherPersonId)).thenReturn(Optional.of(target));
        when(genealogyQuery.findDirectoryPersonByUserId(ACTOR_ID)).thenReturn(Optional.of(actorPerson));

        ApiException error = assertThrows(ApiException.class, () -> service.updateProfessionProfile(
                otherPersonId, new ProfessionProfileRequest("Giáo viên", null, null)));

        assertEquals(ErrorCode.PERM_001, error.code());
        verify(professionRepository, never()).saveAndFlush(any());
    }

    @Test
    void parentCanEditChildProfile() {
        DirectoryPerson child = person(CHILD_ID, null, LocalDate.of(2015, 1, 1), false);
        DirectoryPerson parent = person(PERSON_ID, ACTOR_ID, LocalDate.of(1980, 1, 1), false);
        when(genealogyQuery.findDirectoryPerson(CHILD_ID)).thenReturn(Optional.of(child));
        when(genealogyQuery.findDirectoryPersonByUserId(ACTOR_ID)).thenReturn(Optional.of(parent));
        when(genealogyQuery.isParentOf(PERSON_ID, CHILD_ID)).thenReturn(true);
        when(educationRepository.findByPersonIdAndDeletedAtIsNull(CHILD_ID)).thenReturn(Optional.empty());
        when(educationRepository.saveAndFlush(any(EducationProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateEducationProfile(CHILD_ID,
                new EducationProfileRequest("Trường Tiểu học", null, null));

        assertTrue(result.created());
        assertEquals("Trường Tiểu học", result.data().school());
    }

    @Test
    void parentCannotEditAdultChildProfile() {
        DirectoryPerson adultChild = person(CHILD_ID, null, LocalDate.of(2000, 1, 1), false);
        DirectoryPerson parent = person(PERSON_ID, ACTOR_ID, LocalDate.of(1975, 1, 1), false);
        when(genealogyQuery.findDirectoryPerson(CHILD_ID)).thenReturn(Optional.of(adultChild));
        when(genealogyQuery.findDirectoryPersonByUserId(ACTOR_ID)).thenReturn(Optional.of(parent));

        ApiException error = assertThrows(ApiException.class, () -> service.updateEducationProfile(
                CHILD_ID, new EducationProfileRequest("Đại học", null, null)));

        assertEquals(ErrorCode.PERM_001, error.code());
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void branchAdminCanEditMembersInManagedBranch() {
        when(membershipQuery.getMembership(ACTOR_ID)).thenReturn(
                new MembershipView(ACTOR_ID, FAMILY_ID, BRANCH_ID, EffectiveRole.BRANCH_ADMIN, true));
        DirectoryPerson branchMember = new DirectoryPerson(CHILD_ID, FAMILY_ID, BRANCH_ID,
                "Thành viên trong chi", LocalDate.of(1990, 1, 1), 1990,
                null, false, null, null);
        when(genealogyQuery.findDirectoryPerson(CHILD_ID)).thenReturn(Optional.of(branchMember));
        when(genealogyQuery.isBranchWithin(FAMILY_ID, BRANCH_ID, BRANCH_ID)).thenReturn(true);
        when(professionRepository.findByPersonIdAndDeletedAtIsNull(CHILD_ID)).thenReturn(Optional.empty());
        when(professionRepository.saveAndFlush(any(ProfessionProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateProfessionProfile(CHILD_ID,
                new ProfessionProfileRequest("Bác sĩ", null, null));

        assertTrue(result.created());
        verify(genealogyQuery).isBranchWithin(FAMILY_ID, BRANCH_ID, BRANCH_ID);
    }

    @Test
    void branchAdminCannotEditMemberOutsideManagedBranch() {
        when(membershipQuery.getMembership(ACTOR_ID)).thenReturn(
                new MembershipView(ACTOR_ID, FAMILY_ID, BRANCH_ID, EffectiveRole.BRANCH_ADMIN, true));
        DirectoryPerson branchMember = new DirectoryPerson(CHILD_ID, FAMILY_ID, UUID.randomUUID(),
                "Thành viên ngoài chi", LocalDate.of(1990, 1, 1), 1990,
                null, false, null, null);
        when(genealogyQuery.findDirectoryPerson(CHILD_ID)).thenReturn(Optional.of(branchMember));
        when(genealogyQuery.isBranchWithin(FAMILY_ID, branchMember.branchId(), BRANCH_ID)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.updateProfessionProfile(
                CHILD_ID, new ProfessionProfileRequest("Bác sĩ", null, null)));

        assertEquals(ErrorCode.PERM_001, error.code());
        verify(professionRepository, never()).saveAndFlush(any());
    }

    @Test
    void memberCannotViewChildEducationProfileUnlessParent() {
        DirectoryPerson child = person(CHILD_ID, null, LocalDate.of(2015, 1, 1), false);
        DirectoryPerson unrelated = person(PERSON_ID, ACTOR_ID, LocalDate.of(1980, 1, 1), false);
        when(genealogyQuery.findDirectoryPerson(CHILD_ID)).thenReturn(Optional.of(child));
        when(genealogyQuery.findDirectoryPersonByUserId(ACTOR_ID)).thenReturn(Optional.of(unrelated));

        ApiException error = assertThrows(ApiException.class, () -> service.getEducationProfile(CHILD_ID));

        assertEquals(ErrorCode.PERM_001, error.code());
        verify(educationRepository, never()).findByPersonIdAndDeletedAtIsNull(CHILD_ID);
    }

    @Test
    void graduationYearCannotExceedTenYearsAhead() {
        DirectoryPerson person = person(PERSON_ID, ACTOR_ID, LocalDate.of(1990, 1, 1), false);
        when(genealogyQuery.findDirectoryPerson(PERSON_ID)).thenReturn(Optional.of(person));
        when(genealogyQuery.findDirectoryPersonByUserId(ACTOR_ID)).thenReturn(Optional.of(person));

        ApiException error = assertThrows(ApiException.class, () -> service.updateEducationProfile(
                PERSON_ID, new EducationProfileRequest("University", null, 2037)));

        assertEquals(ErrorCode.VALID_001, error.code());
        verify(educationRepository, never()).saveAndFlush(any());
    }

    @Test
    void memberSearchHidesChildWorkAndLocationFields() {
        DirectoryPerson adult = new DirectoryPerson(PERSON_ID, FAMILY_ID, null, "Người lớn",
                LocalDate.of(1990, 1, 1), 1990, "Hà Nội", false, ACTOR_ID, 3);
        DirectoryPerson child = new DirectoryPerson(CHILD_ID, FAMILY_ID, null, "Trẻ em",
                LocalDate.of(2015, 1, 1), 2015, "Đà Nẵng", false, null, 5);
        when(genealogyQuery.searchDirectoryPeople(eq(FAMILY_ID), eq(null), eq(null), eq(LocalDate.now(CLOCK)), eq(null),
                eq(false), eq("fullName"), eq(true), eq(0), eq(20)))
                .thenReturn(new DirectoryPage(List.of(adult, child), 2));
        when(professionRepository.findAllByPersonIdInAndDeletedAtIsNull(anyCollection())).thenReturn(List.of());

        var result = service.searchMembers(null, null, null, null, 0, 20, "fullName,asc");

        assertEquals(2, result.data().size());
        assertEquals(3, result.data().get(0).generation());
        assertNull(result.data().get(1).jobTitle());
        assertNull(result.data().get(1).location());
        assertNull(result.data().get(1).generation());
        assertNull(result.data().get(1).relationship());
    }

    @Test
    void memberSearchExcludesChildrenWhenFilteringByWorkOrLocation() {
        when(genealogyQuery.searchDirectoryPeople(eq(FAMILY_ID), eq("Hà Nội"), eq(null), eq(LocalDate.now(CLOCK)), eq(null),
                eq(true), eq("createdAt"), eq(false), eq(0), eq(20)))
                .thenReturn(new DirectoryPage(List.of(), 0));
        service.searchMembers(null, null, "Hà Nội", null, 0, 20, "createdAt,desc");

        verify(genealogyQuery).searchDirectoryPeople(eq(FAMILY_ID), eq("Hà Nội"), eq(null), eq(LocalDate.now(CLOCK)), eq(null),
                eq(true), eq("createdAt"), eq(false), eq(0), eq(20));
    }

    private static DirectoryPerson person(UUID id, UUID userId, LocalDate birthDate, boolean deceased) {
        return new DirectoryPerson(id, FAMILY_ID, null, "Thành viên", birthDate, birthDate.getYear(),
                null, deceased, userId, null);
    }
}
