package com.familyconnect.modules.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record SummaryRequest(@NotNull @Pattern(regexp = "POST|STORY|DOCUMENT") String sourceType,
                             @NotNull UUID sourceId) {}
