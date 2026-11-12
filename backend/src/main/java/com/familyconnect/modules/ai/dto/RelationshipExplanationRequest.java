package com.familyconnect.modules.ai.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RelationshipExplanationRequest(@NotNull UUID personAId, @NotNull UUID personBId) { }
