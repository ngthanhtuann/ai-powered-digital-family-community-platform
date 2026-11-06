package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.dto.BackupDtos.RestoreCreateRequest;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupServiceTest {
    @TempDir Path directory;

    private final UUID adminId = UUID.randomUUID();
    private UserRoleRepository roles;
    private BackupService service;

    @BeforeEach
    void setUp() {
        roles = mock(UserRoleRepository.class);
        service = new BackupService(roles, mock(AuditLogger.class), mock(JdbcTemplate.class),
                Clock.fixed(Instant.parse("2026-11-05T01:02:03Z"), ZoneOffset.UTC), directory.toString());
    }

    @Test
    void deniesMembersEvenForList() {
        when(roles.findRoleCodes(adminId)).thenReturn(List.of(Role.MEMBER));
        assertThatThrownBy(() -> service.listBackups(adminId, 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.PERM_001));
    }

    @Test
    void requiresExactConfirmationBeforeRestore() {
        when(roles.findRoleCodes(adminId)).thenReturn(List.of(Role.SYSTEM_ADMIN));
        assertThatThrownBy(() -> service.restore(adminId,
                new RestoreCreateRequest("familyconnect-20261105-010203", "restore")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.VALID_001));
    }

    @Test
    void missingBackupIs404WithoutRunningScript() {
        when(roles.findRoleCodes(adminId)).thenReturn(List.of(Role.SYSTEM_ADMIN));
        assertThatThrownBy(() -> service.restore(adminId,
                new RestoreCreateRequest("familyconnect-20261105-010203", "RESTORE")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.NOT_FOUND_001));
    }

    @Test
    void listsOnlyMatchingArchivesNewestFirstWithPagination() throws Exception {
        when(roles.findRoleCodes(adminId)).thenReturn(List.of(Role.SYSTEM_ADMIN));
        Files.writeString(directory.resolve("familyconnect-20261105-010203.tar.gz"), "backup-a");
        Files.writeString(directory.resolve("familyconnect-20261106-020304.tar.gz"), "backup-b");
        Files.writeString(directory.resolve(".operation.lock"), "");

        var result = service.listBackups(adminId, 0, 1);
        assertThat(result.meta().total()).isEqualTo(2);
        assertThat(result.data()).extracting("id").containsExactly("familyconnect-20261106-020304");
        assertThat(service.listBackups(adminId, 1, 1).data()).extracting("id")
                .containsExactly("familyconnect-20261105-010203");
    }
}
