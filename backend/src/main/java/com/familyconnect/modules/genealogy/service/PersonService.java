package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.community.api.CommunityQuery;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.PersonInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.ListResponse;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.PersonData;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UC-GEN-03: thành viên (BR-GEN-01, 04, 08, 09, 10, 11, 14, 16, 17, 18, 19). */
@Service
public class PersonService {

    private static final Logger log = LoggerFactory.getLogger(PersonService.class);
    private static final Set<String> SORT_FIELDS = Set.of("fullName", "birthDate", "createdAt", "updatedAt");
    private static final int CHILD_AGE = 16;
    private static final int MIN_BIRTH_YEAR = 1000;

    private final PersonRepository personRepository;
    private final FamilyRepository familyRepository;
    private final BranchRepository branchRepository;
    private final GenealogyAccess access;
    private final MembershipQuery membershipQuery;
    private final AuditLogger auditLogger;
    private final AiGateway aiGateway;
    private final CommunityQuery communityQuery;
    private final Clock clock;

    public PersonService(PersonRepository personRepository, FamilyRepository familyRepository,
                         BranchRepository branchRepository, GenealogyAccess access, MembershipQuery membershipQuery,
                         AuditLogger auditLogger, AiGateway aiGateway, CommunityQuery communityQuery,
                         Clock clock) {
        this.personRepository = personRepository;
        this.familyRepository = familyRepository;
        this.branchRepository = branchRepository;
        this.access = access;
        this.membershipQuery = membershipQuery;
        this.auditLogger = auditLogger;
        this.aiGateway = aiGateway;
        this.communityQuery = communityQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResponse<PersonData> list(UUID familyId, String q, UUID branchId, int page, int size, String sort) {
        Viewer viewer = access.requireRead(familyId);
        requireFamily(familyId);
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Person> found = personRepository.search(familyId, branchId, query,
                Paging.of(page, size, sort, SORT_FIELDS));
        return ListResponse.of(found.map(p -> toData(p, viewer)).getContent(), page, size, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PersonData get(UUID personId) {
        Person person = find(personId);
        Viewer viewer = access.requireRead(person.getFamilyId());
        return toData(person, viewer);
    }

    @Transactional
    public PersonData create(UUID familyId, PersonInput input) {
        Viewer viewer = access.requireRead(familyId);
        requireFamily(familyId);
        access.requireManage(viewer, familyId, input.branchId());
        validate(familyId, null, input);
        Person person = new Person();
        person.setFamilyId(familyId);
        apply(person, input);
        person.setCreatedBy(viewer.userId());
        personRepository.saveAndFlush(person);
        auditLogger.log("PERSON_CREATED", "PERSON", person.getId(), familyId, Map.of());
        indexAfterCommit(person);
        return toData(person, viewer);
    }

    @Transactional
    public PersonData update(UUID personId, PersonInput input) {
        Person person = find(personId);
        UUID familyId = person.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        // Cần quyền ở cả chi hiện tại và chi mới (BR-GEN-14).
        access.requireManage(viewer, familyId, person.getBranchId());
        access.requireManage(viewer, familyId, input.branchId());
        validate(familyId, personId, input);
        boolean becameDeceased = !person.isDeceased() && input.deceased();
        apply(person, input);
        person.setUpdatedBy(viewer.userId());
        personRepository.saveAndFlush(person);
        if (becameDeceased) {
            personRepository.widowMarriages(personId, person.getDeathDate(), viewer.userId()); // BR-GEN-08
        }
        auditLogger.log("PERSON_UPDATED", "PERSON", personId, familyId, Map.of());
        indexAfterCommit(person);
        return toData(person, viewer);
    }

    @Transactional
    public void delete(UUID personId) {
        Person person = find(personId);
        UUID familyId = person.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, person.getBranchId());
        if (personRepository.countActiveRelations(personId) > 0) {
            throw new ApiException(ErrorCode.GEN_004); // BR-GEN-09
        }
        person.setDeletedAt(OffsetDateTime.now(clock));
        person.setUpdatedBy(viewer.userId());
        auditLogger.log("PERSON_DELETED", "PERSON", personId, familyId, Map.of());
        afterCommit(() -> aiGateway.removeSource(AiGateway.SourceType.PERSON, personId));
    }

    /** BR-GEN-04, 08, 16, 17, 19 và UC-GEN-03 luồng 3a đến 3f. */
    private void validate(UUID familyId, UUID personId, PersonInput input) {
        LocalDate today = LocalDate.now(clock);
        if (!input.deceased() && input.birthDate() == null) {
            throw ApiException.invalidField("birthDate", "Người còn sống phải có ngày sinh đầy đủ");
        }
        if (input.birthDate() != null && input.birthDate().isAfter(today)) {
            throw ApiException.invalidField("birthDate", "Ngày sinh không được sau hôm nay");
        }
        if (input.birthYear() != null && (input.birthYear() < MIN_BIRTH_YEAR || input.birthYear() > today.getYear())) {
            throw ApiException.invalidField("birthYear", "Năm sinh không hợp lệ");
        }
        if (input.deathDate() != null) {
            if (!input.deceased()) {
                throw ApiException.invalidField("deathDate", "Chỉ nhập ngày mất khi đánh dấu đã mất");
            }
            if (input.deathDate().isAfter(today)) {
                throw ApiException.invalidField("deathDate", "Ngày mất không được sau hôm nay");
            }
            Integer birthYear = input.birthDate() != null ? Integer.valueOf(input.birthDate().getYear()) : input.birthYear();
            if ((input.birthDate() != null && input.deathDate().isBefore(input.birthDate()))
                    || (birthYear != null && input.deathDate().getYear() < birthYear)) {
                throw ApiException.invalidField("deathDate", "Ngày mất không được trước ngày sinh");
            }
        }
        if (input.branchId() != null
                && branchRepository.findByIdAndFamilyIdAndDeletedAtIsNull(input.branchId(), familyId).isEmpty()) {
            throw ApiException.invalidField("branchId", "Chi họ không thuộc gia đình này");
        }
        if (input.avatarMediaId() != null) {
            // Ảnh phải được tải lên cho chính hồ sơ này (ownerType PERSON, ownerId = id hồ sơ), nên luôn cùng gia đình.
            boolean ownAvatar = personId != null && communityQuery.getPersonMedia(input.avatarMediaId())
                    .filter(media -> media.personId().equals(personId)).isPresent();
            if (!ownAvatar) {
                throw ApiException.invalidField("avatarMediaId", personId == null
                        ? "Tạo hồ sơ trước, sau đó tải ảnh đại diện cho hồ sơ và gán bằng PUT"
                        : "Ảnh không tồn tại hoặc không phải ảnh của hồ sơ này");
            }
        }
        if (input.userId() != null) {
            if (isChild(input.deceased(), input.birthDate(), today)) {
                throw new ApiException(ErrorCode.GEN_012);
            }
            MembershipView account = membershipQuery.getMembership(input.userId());
            if (!account.verified() || !familyId.equals(account.familyId())) {
                throw new ApiException(ErrorCode.GEN_014);
            }
            personRepository.findByUserIdAndDeletedAtIsNull(input.userId())
                    .filter(other -> !other.getId().equals(personId))
                    .ifPresent(other -> {
                        throw new ApiException(ErrorCode.CONFLICT_001, "Tài khoản đã được gắn với hồ sơ khác");
                    });
        }
    }

    /** PUT thay toàn bộ trường trừ userId; birth_year chỉ lưu khi không có ngày sinh đầy đủ (BR-GEN-17). */
    private static void apply(Person person, PersonInput input) {
        person.setFullName(input.fullName().trim());
        person.setGender(input.gender().name());
        person.setBirthDate(input.birthDate());
        person.setBirthYear(input.birthDate() == null && input.birthYear() != null
                ? input.birthYear().shortValue() : null);
        person.setBirthplace(FamilyService.blankToNull(input.birthplace()));
        person.setCurrentProvince(FamilyService.blankToNull(input.currentProvince()));
        person.setCurrentAddress(FamilyService.blankToNull(input.currentAddress()));
        person.setBranchId(input.branchId());
        person.setDeceased(input.deceased());
        person.setDeathDate(input.deathDate());
        person.setBurialPlace(FamilyService.blankToNull(input.burialPlace()));
        person.setAvatarMediaId(input.avatarMediaId());
        // Response không trả userId nên client không gửi lại được: để trống thì giữ liên kết (BR-GEN-19).
        if (input.userId() != null) {
            person.setUserId(input.userId());
        }
        person.setNote(FamilyService.blankToNull(input.note()));
    }

    /** BR-GEN-10, privacy.md mục 3 và 4: ẩn trường theo vai trò người xem và tuổi của người được xem. */
    private PersonData toData(Person p, Viewer viewer) {
        Integer birthYear = birthYear(p);
        boolean full = viewer.seesFullData(p.getFamilyId()) || viewer.userId().equals(p.getUserId());
        if (!full && isChild(p.isDeceased(), p.getBirthDate(), LocalDate.now(clock))) {
            return new PersonData(p.getId(), p.getFamilyId(), null, p.getFullName(), p.getGender(), birthYear,
                    null, null, null, null, p.isDeceased(), null, null, null, null, p.getCreatedAt(), p.getUpdatedAt());
        }
        return new PersonData(p.getId(), p.getFamilyId(), p.getBranchId(), p.getFullName(), p.getGender(), birthYear,
                full ? p.getBirthDate() : null, p.getBirthplace(), p.getCurrentProvince(),
                full ? p.getCurrentAddress() : null, p.isDeceased(), p.getDeathDate(), p.getBurialPlace(),
                p.getAvatarMediaId(), p.getNote(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static Integer birthYear(Person p) {
        if (p.getBirthDate() != null) {
            return p.getBirthDate().getYear();
        }
        return p.getBirthYear() == null ? null : p.getBirthYear().intValue();
    }

    private static boolean isChild(boolean deceased, LocalDate birthDate, LocalDate today) {
        return !deceased && birthDate != null && birthDate.plusYears(CHILD_AGE).isAfter(today);
    }

    /** Nội dung cho chỉ mục AI theo BR-AI-07, BR-AI-14, BR-GEN-18: không ngày sinh đầy đủ, không địa chỉ chi tiết. */
    private void indexAfterCommit(Person p) {
        List<String> parts = new ArrayList<>();
        parts.add("Họ tên: " + p.getFullName());
        if (!isChild(p.isDeceased(), p.getBirthDate(), LocalDate.now(clock))) {
            Integer birthYear = birthYear(p);
            if (birthYear != null) {
                parts.add("Năm sinh: " + birthYear);
            }
            if (p.getCurrentProvince() != null) {
                parts.add("Tỉnh/thành: " + p.getCurrentProvince());
            }
            if (p.isDeceased()) {
                parts.add(p.getDeathDate() == null ? "Đã mất" : "Đã mất năm " + p.getDeathDate().getYear());
            }
        }
        String content = String.join(". ", parts) + ".";
        UUID id = p.getId();
        UUID familyId = p.getFamilyId();
        UUID branchId = p.getBranchId();
        afterCommit(() -> aiGateway.upsertSource(AiGateway.SourceType.PERSON, id, familyId, branchId, content));
    }

    private static void afterCommit(Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                log.warn("Không cập nhật được chỉ mục AI cho thành viên", ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safe.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safe.run();
            }
        });
    }

    private void requireFamily(UUID familyId) {
        if (familyRepository.findByIdAndDeletedAtIsNull(familyId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
    }

    private Person find(UUID personId) {
        return personRepository.findByIdAndDeletedAtIsNull(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }
}
