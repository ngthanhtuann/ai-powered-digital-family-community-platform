package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Chạy trên PostgreSQL thật (V5__genealogy_tables.sql), dữ liệu giả. */
@SpringBootTest
@Transactional
class GenealogyPortImplTest {

    @Autowired
    private GenealogyPort port;

    @Autowired
    private GenealogyQuery query;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private final UUID creator = UUID.randomUUID();
    private UUID familyA;
    private UUID familyB;
    private UUID root;
    private UUID child;
    private UUID grandChild;
    private UUID otherRoot;

    @BeforeEach
    void seed() {
        familyA = insertFamily("Họ Nguyễn");
        familyB = insertFamily("Họ Trần");
        root = insertBranch(familyA, "Chi trưởng", null);
        child = insertBranch(familyA, "Chi hai", root);
        grandChild = insertBranch(familyA, "Chi ba", child);
        otherRoot = insertBranch(familyA, "Chi thứ", null);
    }

    @Test
    void namesComeFromActiveRowsOfTheSameFamily() {
        assertThat(port.findFamilyName(familyA)).contains("Họ Nguyễn");
        assertThat(port.findBranchName(familyA, child)).contains("Chi hai");
        assertThat(port.findBranchName(familyB, child)).isEmpty();

        jdbc.update("update family set deleted_at = now() where id = ?", familyB);
        assertThat(port.findFamilyName(familyB)).isEmpty();
    }

    @Test
    void branchWithinCoversItselfAndDescendantsOnly() {
        assertThat(port.isBranchWithin(familyA, root, root)).isTrue();
        assertThat(port.isBranchWithin(familyA, grandChild, root)).isTrue();
        assertThat(port.isBranchWithin(familyA, root, grandChild)).isFalse();
        assertThat(port.isBranchWithin(familyA, child, otherRoot)).isFalse();
        assertThat(port.isBranchWithin(familyB, grandChild, root)).isFalse();
        assertThat(port.isBranchWithin(familyA, null, root)).isFalse();
    }

    @Test
    void linkAccountSetsUserAndMovesItFromTheOldProfile() {
        UUID user = UUID.randomUUID();
        UUID first = insertPerson(familyA, LocalDate.of(1980, 1, 1), false);
        UUID second = insertPerson(familyA, LocalDate.of(1985, 1, 1), false);

        port.linkAccount(familyA, first, user);
        port.linkAccount(familyA, second, user);

        assertThat(userOf(first)).isNull();
        assertThat(userOf(second)).isEqualTo(user);

        port.unlinkAccount(user);
        assertThat(userOf(second)).isNull();
    }

    @Test
    void linkAccountRejectsOtherFamilyUnder16AndTakenProfile() {
        UUID adultA = insertPerson(familyA, LocalDate.of(1980, 1, 1), false);
        UUID kid = insertPerson(familyA, LocalDate.now().minusYears(10), false);

        assertCode(() -> port.linkAccount(familyB, adultA, UUID.randomUUID()), ErrorCode.GEN_014);
        assertCode(() -> port.linkAccount(familyA, kid, UUID.randomUUID()), ErrorCode.GEN_012);
        assertCode(() -> port.linkAccount(familyA, UUID.randomUUID(), UUID.randomUUID()), ErrorCode.NOT_FOUND_001);

        port.linkAccount(familyA, adultA, UUID.randomUUID());
        assertCode(() -> port.linkAccount(familyA, adultA, UUID.randomUUID()), ErrorCode.CONFLICT_001);
    }

    @Test
    void genealogyQueryReturnsFamilyOfActivePersonOnly() {
        UUID person = insertPerson(familyA, LocalDate.of(1950, 1, 1), true);
        assertThat(query.findPersonFamilyId(person)).contains(familyA);

        jdbc.update("update person set deleted_at = now() where id = ?", person);
        entityManager.clear(); // bỏ bản đã nạp vào JPA để đọc lại từ DB
        assertThat(query.findPersonFamilyId(person)).isEmpty();
        assertThat(query.findPersonFamilyId(UUID.randomUUID())).isEmpty();
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(code);
    }

    private UUID insertFamily(String name) {
        return jdbc.queryForObject("insert into family(name, created_by) values (?, ?) returning id",
                UUID.class, name, creator);
    }

    private UUID insertBranch(UUID familyId, String name, UUID parent) {
        return jdbc.queryForObject(
                "insert into branch(family_id, name, parent_branch_id, created_by) values (?, ?, ?, ?) returning id",
                UUID.class, familyId, name, parent, creator);
    }

    private UUID insertPerson(UUID familyId, LocalDate birthDate, boolean deceased) {
        return jdbc.queryForObject("""
                insert into person(family_id, full_name, gender, birth_date, is_deceased, created_by)
                values (?, 'Người thử', 'MALE', ?, ?, ?) returning id
                """, UUID.class, familyId, birthDate, deceased, creator);
    }

    private UUID userOf(UUID personId) {
        entityManager.flush(); // đưa thay đổi của JPA xuống DB trước khi đọc bằng JDBC
        return jdbc.queryForObject("select user_id from person where id = ?", UUID.class, personId);
    }
}
