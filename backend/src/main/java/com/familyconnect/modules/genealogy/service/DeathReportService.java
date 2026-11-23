package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.UserDirectory;
import com.familyconnect.modules.community.api.CommunityNotificationPort;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.DeathReportInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.DeathReportData;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BR-GEN-24 (SCRUM-135): con ruột báo mất, người khác xác nhận (một người là đủ), hết hạn sau 7 ngày,
 * gợi ý kế nhiệm khi người mất là trưởng họ/chi. Bảng death_report thuộc module Gia phả.
 */
@Service
public class DeathReportService {

    static final Duration TTL = Duration.ofDays(7);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private record Report(UUID id, UUID familyId, UUID personId, LocalDate deathDate, LocalTime deathTime, String burialPlace,
                          String status, OffsetDateTime expiresAt, UUID createdBy, UUID successorPersonId) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonRepository personRepository;
    private final BranchRepository branchRepository;
    private final PersonService personService;
    private final GenealogyAccess access;
    private final MembershipQuery membershipQuery;
    private final UserDirectory userDirectory;
    private final CommunityNotificationPort notifications;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public DeathReportService(NamedParameterJdbcTemplate jdbc, PersonRepository personRepository, BranchRepository branchRepository,
                              PersonService personService, GenealogyAccess access, MembershipQuery membershipQuery,
                              UserDirectory userDirectory, CommunityNotificationPort notifications, AuditLogger auditLogger,
                              Clock clock) {
        this.jdbc = jdbc;
        this.personRepository = personRepository;
        this.branchRepository = branchRepository;
        this.personService = personService;
        this.access = access;
        this.membershipQuery = membershipQuery;
        this.userDirectory = userDirectory;
        this.notifications = notifications;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public DeathReportData report(UUID personId, DeathReportInput input) {
        Person person = person(personId);
        Viewer viewer = access.requireRead(person.getFamilyId());
        if (person.isDeceased()) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Người này đã được đánh dấu đã mất");
        }
        if (viewer.userId().equals(person.getUserId()) || !personRepository.isBiologicalChildAccount(personId, viewer.userId())) {
            throw new ApiException(ErrorCode.PERM_001, "Chỉ con ruột có tài khoản mới gửi báo mất; người quản lý chi dùng Báo mất trực tiếp");
        }
        personService.validateDeathDate(person, input.deathDate());
        expireStale(personId);
        if (pending(personId) != null) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Đã có báo mất đang chờ xác nhận");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalTime time = input.deathTime() == null || input.deathTime().isBlank() ? null : LocalTime.parse(input.deathTime());
        String burial = input.burialPlace() == null || input.burialPlace().isBlank() ? null : input.burialPlace().trim();
        UUID id = jdbc.queryForObject("""
                INSERT INTO death_report (family_id, person_id, death_date, death_time, burial_place, expires_at, created_by, updated_by)
                VALUES (:family, :person, :date, :time, :burial, :expires, :actor, :actor) RETURNING id
                """, new MapSqlParameterSource().addValue("family", person.getFamilyId()).addValue("person", personId)
                .addValue("date", input.deathDate()).addValue("time", time).addValue("burial", burial)
                .addValue("expires", now.plus(TTL)).addValue("actor", viewer.userId()), UUID.class);
        String reporter = userDirectory.getFullNames(List.of(viewer.userId())).getOrDefault(viewer.userId(), "Một người con");
        String text = reporter + " báo " + person.getFullName() + " đã mất ngày " + input.deathDate().format(DATE)
                + (time == null ? "" : " lúc " + time.format(HH_MM)) + ". Mở hồ sơ để xác nhận trong 7 ngày.";
        notifications.sendDeathReport(confirmers(person, viewer.userId()), person.getFamilyId(), personId,
                "Cần xác nhận báo mất: " + person.getFullName(), text, viewer.userId());
        auditLogger.log("DEATH_REPORTED", "PERSON", personId, person.getFamilyId(), Map.of("reportId", id));
        return toData(load(id), person, viewer);
    }

    @Transactional
    public DeathReportData latest(UUID personId) {
        Person person = person(personId);
        Viewer viewer = access.requireRead(person.getFamilyId());
        expireStale(personId);
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM death_report WHERE person_id = :person AND status IN ('PENDING', 'CONFIRMED') AND deleted_at IS NULL
                ORDER BY created_at DESC LIMIT 1
                """, Map.of("person", personId), UUID.class);
        return ids.isEmpty() ? null : toData(load(ids.get(0)), person, viewer);
    }

    @Transactional
    public DeathReportData confirm(UUID reportId) {
        Report report = load(reportId);
        Person person = person(report.personId());
        Viewer viewer = access.requireRead(person.getFamilyId());
        expireStale(person.getId());
        report = load(reportId);
        if (!"PENDING".equals(report.status())) {
            throw new ApiException(ErrorCode.CONFLICT_001, "EXPIRED".equals(report.status())
                    ? "Báo mất đã hết hạn" : "Báo mất đã được xác nhận");
        }
        if (!canConfirm(viewer, person, report)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        boolean head = isHead(person);
        personService.markDeceasedByReport(person, report.deathDate(),
                report.deathTime() == null ? null : report.deathTime().format(HH_MM), report.burialPlace(), viewer.userId());
        UUID successor = head ? successor(person.getId()) : null;
        jdbc.update("UPDATE death_report SET successor_person_id = :successor, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource().addValue("successor", successor).addValue("id", reportId));
        auditLogger.log("DEATH_REPORT_CONFIRMED", "PERSON", person.getId(), person.getFamilyId(),
                successor == null ? Map.of("reportId", reportId) : Map.of("reportId", reportId, "successorPersonId", successor));
        return toData(load(reportId), person, viewer);
    }

    // ---- người xác nhận và kế nhiệm ----

    /** Ai nhận thông báo cần xác nhận (BR-GEN-24); người quản lý chi luôn xác nhận được dù không có trong danh sách. */
    Set<UUID> confirmers(Person person, UUID reporter) {
        Set<UUID> users = new LinkedHashSet<>();
        if (isHead(person)) {
            users.addAll(accounts(CHILDREN, person.getId()));
            users.remove(reporter);
            if (users.isEmpty()) {
                users.addAll(accounts(SPOUSES, person.getId()));
                users.addAll(accounts(SIBLINGS, person.getId()));
            }
        } else {
            for (MembershipView m : membershipQuery.findVerifiedMembers(person.getFamilyId())) {
                boolean manages = m.role() == EffectiveRole.BRANCH_ADMIN && (m.branchId() == null
                        || (person.getBranchId() != null && branchRepository.isWithin(person.getFamilyId(), person.getBranchId(), m.branchId())));
                if (manages) {
                    users.add(m.userId());
                }
            }
        }
        users.remove(reporter);
        if (person.getUserId() != null) {
            users.remove(person.getUserId());
        }
        return users;
    }

    private boolean canConfirm(Viewer viewer, Person person, Report report) {
        if (!"PENDING".equals(report.status()) || viewer.userId().equals(report.createdBy())
                || viewer.userId().equals(person.getUserId())) {
            return false;
        }
        return access.canManage(viewer, person.getFamilyId(), person.getBranchId())
                || confirmers(person, report.createdBy()).contains(viewer.userId());
    }

    private boolean isHead(Person person) {
        if (person.getUserId() == null) {
            return false;
        }
        MembershipView m = membershipQuery.getMembership(person.getUserId());
        return m.verified() && m.role() == EffectiveRole.BRANCH_ADMIN && person.getFamilyId().equals(m.familyId());
    }

    private static final String CHILDREN = """
            SELECT c.user_id FROM parent_child pc JOIN person c ON c.id = pc.child_id
            WHERE pc.parent_id = :person AND pc.kind = 'BIOLOGICAL' AND pc.deleted_at IS NULL
              AND c.deleted_at IS NULL AND c.user_id IS NOT NULL AND NOT c.is_deceased
            """;
    private static final String SPOUSES = """
            SELECT o.user_id FROM marriage m
            JOIN person o ON o.id = CASE WHEN m.person1_id = :person THEN m.person2_id ELSE m.person1_id END
            WHERE (m.person1_id = :person OR m.person2_id = :person) AND m.status = 'MARRIED' AND m.deleted_at IS NULL
              AND o.deleted_at IS NULL AND o.user_id IS NOT NULL AND NOT o.is_deceased
            """;
    private static final String SIBLINGS = """
            SELECT DISTINCT s.user_id FROM parent_child a
            JOIN parent_child b ON b.parent_id = a.parent_id AND b.child_id <> a.child_id
            JOIN person s ON s.id = b.child_id
            WHERE a.child_id = :person AND a.kind = 'BIOLOGICAL' AND b.kind = 'BIOLOGICAL'
              AND a.deleted_at IS NULL AND b.deleted_at IS NULL AND s.deleted_at IS NULL
              AND s.user_id IS NOT NULL AND NOT s.is_deceased
            """;
    /** Con trai ruột, lớn tuổi trước; kèm còn sống và có tài khoản. */
    private static final String SONS = """
            SELECT c.id, (c.user_id IS NOT NULL AND NOT c.is_deceased) AS eligible FROM parent_child pc JOIN person c ON c.id = pc.child_id
            WHERE pc.parent_id = :person AND pc.kind = 'BIOLOGICAL' AND pc.deleted_at IS NULL
              AND c.deleted_at IS NULL AND c.gender = 'MALE'
            ORDER BY COALESCE(c.birth_date, make_date(c.birth_year::int, 1, 1)) NULLS LAST, c.full_name
            """;

    private List<UUID> accounts(String sql, UUID personId) {
        return jdbc.queryForList(sql, Map.of("person", personId), UUID.class);
    }

    private record Son(UUID id, boolean eligible) {
    }

    private List<Son> sons(UUID personId) {
        return jdbc.query(SONS, Map.of("person", personId),
                (rs, i) -> new Son(rs.getObject("id", UUID.class), rs.getBoolean("eligible")));
    }

    /** Con trai ruột lớn nhất còn sống có tài khoản; không có thì cháu đích tôn (con trai lớn nhất hợp lệ của con trai trưởng). */
    UUID successor(UUID personId) {
        List<Son> sons = sons(personId);
        for (Son son : sons) {
            if (son.eligible()) {
                return son.id();
            }
        }
        if (sons.isEmpty()) {
            return null;
        }
        return sons(sons.get(0).id()).stream().filter(Son::eligible).map(Son::id).findFirst().orElse(null);
    }

    // ---- truy cập bảng ----

    private void expireStale(UUID personId) {
        int expired = jdbc.update("""
                UPDATE death_report SET status = 'EXPIRED', updated_at = now()
                WHERE person_id = :person AND status = 'PENDING' AND expires_at <= :now AND deleted_at IS NULL
                """, new MapSqlParameterSource().addValue("person", personId).addValue("now", OffsetDateTime.now(clock)));
        if (expired > 0) {
            notifications.retractDeathReport(personId);
        }
    }

    private Report pending(UUID personId) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM death_report WHERE person_id = :person AND status = 'PENDING' AND deleted_at IS NULL",
                Map.of("person", personId), UUID.class);
        return ids.isEmpty() ? null : load(ids.get(0));
    }

    private Report load(UUID id) {
        List<Report> rows = jdbc.query("""
                SELECT id, family_id, person_id, death_date, death_time, burial_place, status, expires_at, created_by, successor_person_id
                FROM death_report WHERE id = :id AND deleted_at IS NULL
                """, Map.of("id", id), (rs, i) -> new Report(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
                rs.getObject("person_id", UUID.class), rs.getObject("death_date", LocalDate.class),
                rs.getObject("death_time", LocalTime.class), rs.getString("burial_place"), rs.getString("status"),
                rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("created_by", UUID.class),
                rs.getObject("successor_person_id", UUID.class)));
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
        return rows.get(0);
    }

    private Person person(UUID personId) {
        return personRepository.findByIdAndDeletedAtIsNull(personId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    private DeathReportData toData(Report r, Person person, Viewer viewer) {
        String reporter = userDirectory.getFullNames(List.of(r.createdBy())).get(r.createdBy());
        String successorName = r.successorPersonId() == null ? null
                : personRepository.findByIdAndDeletedAtIsNull(r.successorPersonId()).map(Person::getFullName).orElse(null);
        return new DeathReportData(r.id(), r.personId(), person.getFullName(), reporter, r.deathDate(),
                r.deathTime() == null ? null : r.deathTime().format(HH_MM), r.burialPlace(), r.status(), r.expiresAt(),
                canConfirm(viewer, person, r), r.successorPersonId(), successorName);
    }
}
