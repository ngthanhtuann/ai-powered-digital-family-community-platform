package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.util.LikePatterns;
import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.util.AfterCommit;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.directory.api.DirectoryQuery;
import com.familyconnect.modules.genealogy.api.PersonSearchIndex;
import com.familyconnect.modules.auth.api.MembershipCommand;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.community.api.CommunityNotificationPort;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.DeathInput;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UC-GEN-03: thành viên (BR-GEN-01, 04, 08, 09, 10, 11, 14, 16, 17, 18, 19). */
@Service
public class PersonService implements PersonSearchIndex {

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
    private final DirectoryQuery directoryQuery;
    private final MembershipCommand membershipCommand;
    private final CommunityNotificationPort notifications;
    private final Clock clock;

    public PersonService(PersonRepository personRepository, FamilyRepository familyRepository,
                         BranchRepository branchRepository, GenealogyAccess access, MembershipQuery membershipQuery,
                         AuditLogger auditLogger, AiGateway aiGateway, CommunityQuery communityQuery,
                         DirectoryQuery directoryQuery, MembershipCommand membershipCommand,
                         CommunityNotificationPort notifications, Clock clock) {
        this.membershipCommand = membershipCommand;
        this.notifications = notifications;
        this.personRepository = personRepository;
        this.familyRepository = familyRepository;
        this.branchRepository = branchRepository;
        this.access = access;
        this.membershipQuery = membershipQuery;
        this.auditLogger = auditLogger;
        this.aiGateway = aiGateway;
        this.communityQuery = communityQuery;
        this.directoryQuery = directoryQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListResponse<PersonData> list(UUID familyId, String q, UUID branchId, int page, int size, String sort) {
        Viewer viewer = access.requireRead(familyId);
        requireFamily(familyId);
        // Escape ký tự đặc biệt của LIKE để "%" hay "_" người dùng gõ được tìm đúng nghĩa đen.
        String pattern = q == null || q.isBlank() ? null : LikePatterns.contains(q.trim());
        Page<Person> found = personRepository.search(familyId, branchId, pattern,
                Paging.of(page, size, sort, SORT_FIELDS));
        return ListResponse.of(found.map(p -> toData(p, viewer)).getContent(), page, size, found.getTotalElements());
    }

    /** Hồ sơ gia phả gắn với tài khoản đang đăng nhập (trang cá nhân của tôi); chưa gắn thì 404. */
    @Transactional(readOnly = true)
    public PersonData mine() {
        UUID userId = access.current().userId();
        Person person = personRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        return toData(person, access.requireRead(person.getFamilyId()));
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
        if (!canEdit(person, viewer)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        // Chính chủ không được tự đổi liên kết tài khoản; gắn tài khoản là việc của người quản lý chi.
        if (input.userId() != null && !input.userId().equals(person.getUserId())
                && !access.canManage(viewer, familyId, person.getBranchId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        // Đổi chi cần quyền quản lý chi mới, kể cả chính chủ (BR-GEN-14).
        if (!java.util.Objects.equals(person.getBranchId(), input.branchId())) {
            access.requireManage(viewer, familyId, input.branchId());
        }
        validate(familyId, personId, input);
        boolean becameDeceased = !person.isDeceased() && input.deceased();
        boolean revived = person.isDeceased() && !input.deceased();
        LocalDate oldDeathDate = person.getDeathDate();
        apply(person, input);
        person.setUpdatedBy(viewer.userId());
        personRepository.saveAndFlush(person);
        if (becameDeceased) {
            onDeath(person, viewer.userId());
        } else if (revived) {
            onRevive(person, oldDeathDate, viewer.userId());
        }
        auditLogger.log("PERSON_UPDATED", "PERSON", personId, familyId, Map.of());
        indexAfterCommit(person);
        return toData(person, viewer);
    }

    /** BR-GEN-21: người quản lý chi báo mất (kể cả hồ sơ đã gắn tài khoản) hoặc khôi phục khi báo nhầm. */
    @Transactional
    public PersonData setDeath(UUID personId, DeathInput input) {
        Person person = find(personId);
        UUID familyId = person.getFamilyId();
        Viewer viewer = access.requireRead(familyId);
        access.requireManage(viewer, familyId, person.getBranchId());
        if (viewer.userId().equals(person.getUserId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        boolean wasDeceased = person.isDeceased();
        LocalDate oldDeathDate = person.getDeathDate();
        if (input.deceased()) {
            validateDeathDate(person, input.deathDate());
            person.setDeceased(true);
            person.setDeathDate(input.deathDate());
            person.setDeathTime(input.deathTime() == null || input.deathTime().isBlank() ? null : LocalTime.parse(input.deathTime()));
            person.setBurialPlace(FamilyService.blankToNull(input.burialPlace()));
        } else {
            if (wasDeceased && person.getBirthDate() == null) {
                throw ApiException.invalidField("birthDate", "Người còn sống cần ngày sinh, hãy nhập ngày sinh trước khi khôi phục");
            }
            person.setDeceased(false);
            person.setDeathDate(null);
            person.setDeathTime(null);
            person.setBurialPlace(null);
        }
        person.setUpdatedBy(viewer.userId());
        personRepository.saveAndFlush(person);
        String action = "PERSON_UPDATED";
        if (input.deceased() && !wasDeceased) {
            onDeath(person, viewer.userId());
            action = "PERSON_DECEASED";
        } else if (!input.deceased() && wasDeceased) {
            onRevive(person, oldDeathDate, viewer.userId());
            action = "PERSON_DECEASED_REVERTED";
        }
        auditLogger.log(action, "PERSON", personId, familyId, Map.of());
        indexAfterCommit(person);
        return toData(person, viewer);
    }

    private void validateDeathDate(Person person, LocalDate deathDate) {
        if (deathDate == null) {
            throw ApiException.invalidField("deathDate", "Cần nhập ngày mất");
        }
        if (deathDate.isAfter(LocalDate.now(clock))) {
            throw ApiException.invalidField("deathDate", "Ngày mất không được sau hôm nay");
        }
        Integer birthYear = birthYear(person);
        if ((person.getBirthDate() != null && deathDate.isBefore(person.getBirthDate()))
                || (birthYear != null && deathDate.getYear() < birthYear)) {
            throw ApiException.invalidField("deathDate", "Ngày mất không được trước ngày sinh");
        }
    }

    /** Hệ quả khi chuyển sang đã mất: BR-GEN-08 góa, BR-AUTH-23 khóa tài khoản, BR-COM-15 thông báo tang. */
    private void onDeath(Person person, UUID actor) {
        personRepository.widowMarriages(person.getId(), person.getDeathDate(), actor);
        if (person.getUserId() != null) {
            membershipCommand.lockDeceasedAccount(person.getUserId(), actor);
        }
        List<UUID> branches = person.getBranchId() == null ? null
                : branchRepository.findBranchAndDescendants(person.getFamilyId(), person.getBranchId());
        notifications.sendDeathNotice(person.getFamilyId(), branches, person.getId(), person.getUserId(),
                "Báo tang: " + person.getFullName(), deathNoticeText(person), actor);
    }

    /** Báo nhầm (BR-GEN-21): hoàn lại góa, mở khóa tài khoản, thu hồi thông báo tang. */
    private void onRevive(Person person, LocalDate oldDeathDate, UUID actor) {
        if (oldDeathDate != null) {
            personRepository.reviveMarriages(person.getId(), oldDeathDate, actor);
        }
        if (person.getUserId() != null) {
            membershipCommand.unlockDeceasedAccount(person.getUserId(), actor);
        }
        notifications.retractDeathNotice(person.getId());
    }

    static String deathNoticeText(Person person) {
        StringBuilder text = new StringBuilder(person.getFullName()).append(" đã mất");
        if (person.getDeathDate() != null) {
            text.append(" ngày ").append(person.getDeathDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
        if (person.getDeathTime() != null) {
            text.append(" lúc ").append(person.getDeathTime().format(HH_MM));
        }
        text.append('.');
        if (person.getBurialPlace() != null) {
            text.append(" Nơi an táng: ").append(person.getBurialPlace()).append('.');
        }
        return text.toString();
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

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
        if (!input.deceased()) {
            person.setDeathTime(null);
        }
        person.setBurialPlace(FamilyService.blankToNull(input.burialPlace()));
        person.setAvatarMediaId(input.avatarMediaId());
        // Response không trả userId nên client không gửi lại được: để trống thì giữ liên kết (BR-GEN-19).
        if (input.userId() != null) {
            person.setUserId(input.userId());
        }
        person.setNote(FamilyService.blankToNull(input.note()));
    }

    /** BR-GEN-10, privacy.md mục 3 và 4: ẩn trường theo vai trò người xem và tuổi của người được xem. */
    /** BR-GEN-20: chính chủ; hoặc người quản lý chi với hồ sơ chưa gắn tài khoản; SYSTEM_ADMIN sửa được mọi hồ sơ. */
    private boolean canEdit(Person p, Viewer viewer) {
        return access.canEditPerson(viewer, p);
    }

    private PersonData toData(Person p, Viewer viewer) {
        Integer birthYear = birthYear(p);
        boolean full = viewer.seesFullData(p.getFamilyId()) || viewer.userId().equals(p.getUserId());
        if (!full && isChild(p.isDeceased(), p.getBirthDate(), LocalDate.now(clock))) {
            return new PersonData(p.getId(), p.getFamilyId(), null, p.getFullName(), p.getGender(), birthYear,
                    null, null, null, null, p.isDeceased(), null, null, null, null, p.getCreatedAt(), p.getUpdatedAt(),
                    null, false, null, false);
        }
        return new PersonData(p.getId(), p.getFamilyId(), p.getBranchId(), p.getFullName(), p.getGender(), birthYear,
                full ? p.getBirthDate() : null, p.getBirthplace(), p.getCurrentProvince(),
                full ? p.getCurrentAddress() : null, p.isDeceased(), p.getDeathDate(), p.getBurialPlace(),
                p.getAvatarMediaId(), p.getNote(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getUserId(), canEdit(p, viewer), p.getDeathTime() == null ? null : p.getDeathTime().format(HH_MM),
                access.canManage(viewer, p.getFamilyId(), p.getBranchId()));
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
            // Nghề nghiệp mức chung (BR-AI-07, BR-AI-14), nguồn là hồ sơ nghề nghiệp của Directory (FR-DIR-02).
            directoryQuery.findJobTitle(p.getId()).ifPresent(job -> parts.add("Nghề nghiệp: " + job));
            if (p.isDeceased()) {
                parts.add(p.getDeathDate() == null ? "Đã mất" : "Đã mất năm " + p.getDeathDate().getYear());
            }
        }
        String content = String.join(". ", parts) + ".";
        UUID id = p.getId();
        UUID familyId = p.getFamilyId();
        // Hồ sơ thành viên cả gia đình được xem (UC-GEN-03), nên chunk PERSON là phạm vi toàn gia đình (BR-AI-13).
        afterCommit(() -> aiGateway.upsertSource(AiGateway.SourceType.PERSON, id, familyId, null, content));
    }

    @Override
    @Transactional(readOnly = true)
    public void refresh(UUID personId) {
        personRepository.findByIdAndDeletedAtIsNull(personId).ifPresent(this::indexAfterCommit);
    }

    private static void afterCommit(Runnable action) {
        AfterCommit.runQuietly(action, ex -> log.warn("Không cập nhật được chỉ mục AI cho thành viên", ex));
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
