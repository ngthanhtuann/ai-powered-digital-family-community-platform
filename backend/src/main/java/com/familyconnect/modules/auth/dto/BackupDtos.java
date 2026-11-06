package com.familyconnect.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/** UC-AUTH-11, schema BackupFile / RestoreCreateRequest / RestoreResult (auth.yaml). */
public final class BackupDtos {
    private BackupDtos() {
    }

    public record BackupFile(String id, String fileName, long fileSizeBytes, OffsetDateTime createdAt) {
    }

    public record RestoreCreateRequest(@NotBlank String backupId, @NotBlank String confirmText) {
    }

    public record RestoreResult(String backupId, OffsetDateTime restoredAt) {
    }
}
