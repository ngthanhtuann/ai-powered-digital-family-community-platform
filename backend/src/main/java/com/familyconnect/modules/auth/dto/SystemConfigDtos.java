package com.familyconnect.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** UC-AUTH-12, schema SystemConfig / ConfigUpdateRequest (auth.yaml). */
public final class SystemConfigDtos {
    private SystemConfigDtos() {
    }

    public enum ConfigValueType { INTEGER, BOOLEAN, STRING }

    public record SystemConfigData(String key, String value, ConfigValueType valueType, String group,
                                   String defaultValue, String description, UUID updatedBy,
                                   OffsetDateTime updatedAt) {
    }

    public record ConfigUpdateRequest(@NotNull @Size(max = 500) String value) {
    }
}
