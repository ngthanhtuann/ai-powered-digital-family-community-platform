package com.familyconnect.modules.ai.dto;

import java.util.UUID;

public record SummaryResponse(String summary, String sourceType, UUID sourceId, boolean cached) {}
