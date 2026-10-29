package com.familyconnect.modules.directory.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.directory.dto.DirectoryRequests.EducationProfileRequest;
import com.familyconnect.modules.directory.dto.DirectoryRequests.ProfessionProfileRequest;
import com.familyconnect.modules.directory.dto.DirectoryResponses.EducationProfileData;
import com.familyconnect.modules.directory.dto.DirectoryResponses.ListResponse;
import com.familyconnect.modules.directory.dto.DirectoryResponses.PersonSearchResult;
import com.familyconnect.modules.directory.dto.DirectoryResponses.ProfessionProfileData;
import com.familyconnect.modules.directory.dto.DirectoryResponses.UpsertResult;
import com.familyconnect.modules.directory.entity.EducationProfile;
import com.familyconnect.modules.directory.entity.ProfessionProfile;
import com.familyconnect.modules.directory.repository.EducationProfileRepository;
import com.familyconnect.modules.directory.repository.ProfessionProfileRepository;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DirectoryService {

    private static final int CHILD_AGE = 16;
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "fullName", "birthYear");

    private final ProfessionProfileRepository professionRepository;
    private final EducationProfileRepository educationRepository;
    private final GenealogyQuery genealogyQuery;
    private final MembershipQuery membershipQuery;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public DirectoryService(ProfessionProfileRepository professionRepository,
                            EducationProfileRepository educationRepository,
                            GenealogyQuery genealogyQuery, MembershipQuery membershipQuery,
                            AuditLogger auditLogger, Clock clock) {
        this.professionRepository = professionRepository;
        this.educationRepository = educationRepository;
        this.genealogyQuery = genealogyQuery;
        this.membershipQuery = membershipQuery;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResponse<PersonSearchResult> searchMembers(UUID requestedFamilyId, String job, String location,
                                                           Integer generation, int page, int size, String sort) {
        UUID actorId = CurrentUser.require().id();
        MembershipView membership = membershipQuery.getMembership(actorId);
        UUID familyId = resolveFamilyId(requestedFamilyId, membership);
        membershipQuery.requireFamilyAccess(actorId, familyId);
        validateSearch(job, location, generation, page, size);
        SortSpec sortSpec = parseSort(sort);

        String normalizedJob = blankToNull(job);
        String normalizedLocation = blankToNull(location);
        Set<UUID> matchingPeople = normalizedJob == null ? null
                : professionRepository.findPersonIdsByJobTitle(familyId, normalizedJob).stream().collect(Collectors.toSet());
        boolean excludeChildren = membership.role() == EffectiveRole.MEMBER
                && (normalizedJob != null || normalizedLocation != null);
        LocalDate today = LocalDate.now(clock);
        DirectoryPage result = genealogyQuery.searchDirectoryPeople(familyId, normalizedLocation, generation, today,
                matchingPeople, excludeChildren, sortSpec.field(), sortSpec.ascending(), page, size);

        List<UUID> personIds = result.people().stream().map(DirectoryPerson::id).toList();
        Map<UUID, String> jobTitles = personIds.isEmpty() ? Map.of()
                : professionRepository.findAllByPersonIdInAndDeletedAtIsNull(personIds)
                        .stream().collect(Collectors.toMap(
                                ProfessionProfile::getPersonId, ProfessionProfile::getJobTitle));
        List<PersonSearchResult> members = result.people().stream().map(person -> {
            boolean child = isChild(person, today);
            return new PersonSearchResult(person.id(), person.fullName(), null, person.birthYear(),
                    child ? null : person.generation(),
                    child ? null : jobTitles.get(person.id()),
                    child ? null : person.currentProvince());
        }).toList();
        return ListResponse.of(members, page, size, result.total());
    }

    @Transactional(readOnly = true)
    public ProfessionProfileData getProfessionProfile(UUID personId) {
        DirectoryPerson person = requirePerson(personId);
        requireFamilyRead(person.familyId());
        requireChildProfileVisibility(person);
        ProfessionProfile profile = professionRepository.findByPersonIdAndDeletedAtIsNull(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        return toData(profile);
    }

    @Transactional
    public UpsertResult<ProfessionProfileData> updateProfessionProfile(
            UUID personId, ProfessionProfileRequest request) {
        DirectoryPerson person = requirePerson(personId);
        UUID actorId = requireProfileWrite(person);
        ProfessionProfile profile = professionRepository.findByPersonIdAndDeletedAtIsNull(personId)
                .orElse(null);
        boolean created = profile == null;
        if (created) {
            profile = new ProfessionProfile(person.familyId(), personId, request.jobTitle().trim(),
                    blankToNull(request.company()), blankToNull(request.workLocation()), actorId);
        } else {
            profile.update(request.jobTitle().trim(), blankToNull(request.company()),
                    blankToNull(request.workLocation()), actorId);
        }
        ProfessionProfile saved = professionRepository.saveAndFlush(profile);
        auditLogger.log(created ? "PROFESSION_PROFILE_CREATED" : "PROFESSION_PROFILE_UPDATED",
                "PROFESSION_PROFILE", saved.getId(), person.familyId(), Map.of());
        return new UpsertResult<>(toData(saved), created);
    }

    @Transactional(readOnly = true)
    public EducationProfileData getEducationProfile(UUID personId) {
        DirectoryPerson person = requirePerson(personId);
        requireFamilyRead(person.familyId());
        requireChildProfileVisibility(person);
        EducationProfile profile = educationRepository.findByPersonIdAndDeletedAtIsNull(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        return toData(profile);
    }

    @Transactional
    public UpsertResult<EducationProfileData> updateEducationProfile(
            UUID personId, EducationProfileRequest request) {
        DirectoryPerson person = requirePerson(personId);
        UUID actorId = requireProfileWrite(person);
        validateGraduationYear(request.graduationYear());
        EducationProfile profile = educationRepository.findByPersonIdAndDeletedAtIsNull(personId)
                .orElse(null);
        boolean created = profile == null;
        if (created) {
            profile = new EducationProfile(person.familyId(), personId, request.school().trim(),
                    blankToNull(request.degree()), request.graduationYear(), actorId);
        } else {
            profile.update(request.school().trim(), blankToNull(request.degree()),
                    request.graduationYear(), actorId);
        }
        EducationProfile saved = educationRepository.saveAndFlush(profile);
        auditLogger.log(created ? "EDUCATION_PROFILE_CREATED" : "EDUCATION_PROFILE_UPDATED",
                "EDUCATION_PROFILE", saved.getId(), person.familyId(), Map.of());
        return new UpsertResult<>(toData(saved), created);
    }

    private UUID requireProfileWrite(DirectoryPerson person) {
        UUID actorId = CurrentUser.require().id();
        requireFamilyRead(person.familyId());
        MembershipView membership = membershipQuery.getMembership(actorId);
        if (membership.role() == EffectiveRole.SYSTEM_ADMIN) {
            return actorId;
        }
        if (membership.role() == EffectiveRole.BRANCH_ADMIN) {
            boolean inScope = membership.branchId() == null
                    || (person.branchId() != null
                    && genealogyQuery.isBranchWithin(person.familyId(), person.branchId(), membership.branchId()));
            if (!inScope) {
                throw new ApiException(ErrorCode.PERM_001);
            }
            return actorId;
        }
        DirectoryPerson ownPerson = genealogyQuery.findDirectoryPersonByUserId(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.PERM_001));
        // UC-DIR-02: chính chủ, hoặc cha mẹ sửa cho con dưới 16 tuổi
        boolean parentOfChild = isChild(person, LocalDate.now(clock))
                && genealogyQuery.isParentOf(ownPerson.id(), person.id());
        if (ownPerson.familyId().equals(person.familyId())
                && (ownPerson.id().equals(person.id()) || parentOfChild)) {
            return actorId;
        }
        throw new ApiException(ErrorCode.PERM_001);
    }

    private void requireFamilyRead(UUID familyId) {
        UUID actorId = CurrentUser.require().id();
        membershipQuery.requireFamilyAccess(actorId, familyId);
    }

    private void requireChildProfileVisibility(DirectoryPerson person) {
        MembershipView membership = membershipQuery.getMembership(CurrentUser.require().id());
        if (membership.role() != EffectiveRole.MEMBER || !isChild(person, LocalDate.now(clock))) {
            return;
        }
        DirectoryPerson ownPerson = genealogyQuery.findDirectoryPersonByUserId(membership.userId()).orElse(null);
        if (ownPerson == null || !ownPerson.familyId().equals(person.familyId())
                || !ownPerson.id().equals(person.id())
                && !genealogyQuery.isParentOf(ownPerson.id(), person.id())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    private DirectoryPerson requirePerson(UUID personId) {
        return genealogyQuery.findDirectoryPerson(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private UUID resolveFamilyId(UUID requestedFamilyId, MembershipView membership) {
        if (membership.role() == EffectiveRole.SYSTEM_ADMIN) {
            if (requestedFamilyId == null) {
                throw ApiException.invalidField("familyId", "SYSTEM_ADMIN phải truyền familyId");
            }
            return requestedFamilyId;
        }
        if (!membership.verified()) {
            membershipQuery.requireVerified(membership.userId());
        }
        if (requestedFamilyId != null && !requestedFamilyId.equals(membership.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        return membership.familyId();
    }

    private void validateSearch(String job, String location, Integer generation, int page, int size) {
        if (job != null && job.length() > 100) {
            throw ApiException.invalidField("job", "job tối đa 100 ký tự");
        }
        if (location != null && location.length() > 100) {
            throw ApiException.invalidField("location", "location tối đa 100 ký tự");
        }
        if (generation != null && generation < 1) {
            throw ApiException.invalidField("generation", "generation phải từ 1 trở lên");
        }
        if (page < 0) {
            throw ApiException.invalidField("page", "page phải bắt đầu từ 0");
        }
        if (size < 1 || size > 100) {
            throw ApiException.invalidField("size", "size phải từ 1 đến 100");
        }
    }

    private SortSpec parseSort(String sort) {
        String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])
                || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw ApiException.invalidField("sort", "Giá trị sort không hợp lệ");
        }
        return new SortSpec(parts[0], parts[1].equalsIgnoreCase("asc"));
    }

    private void validateGraduationYear(Integer year) {
        if (year != null && year > LocalDate.now(clock).getYear() + 10) {
            throw ApiException.invalidField("graduationYear", "Năm tốt nghiệp không được quá 10 năm trong tương lai");
        }
    }

    private static boolean isChild(DirectoryPerson person, LocalDate today) {
        if (person.deceased()) {
            return false;
        }
        if (person.birthDate() != null) {
            return person.birthDate().plusYears(CHILD_AGE).isAfter(today);
        }
        return person.birthYear() != null && person.birthYear() > today.getYear() - CHILD_AGE;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ProfessionProfileData toData(ProfessionProfile profile) {
        return new ProfessionProfileData(profile.getId(), profile.getPersonId(), profile.getJobTitle(),
                profile.getCompany(), profile.getWorkLocation(), profile.getCreatedAt(), profile.getUpdatedAt());
    }

    private static EducationProfileData toData(EducationProfile profile) {
        return new EducationProfileData(profile.getId(), profile.getPersonId(), profile.getSchool(),
                profile.getDegree(), profile.getGraduationYear(), profile.getCreatedAt(), profile.getUpdatedAt());
    }

    private record SortSpec(String field, boolean ascending) {
    }
}
