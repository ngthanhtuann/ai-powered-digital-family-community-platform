package com.familyconnect.modules.ai.dto;

import java.util.UUID;

public record PersonEmbeddingInput(
        UUID personId,
        UUID familyId,
        UUID branchId,
        String fullName,
        Integer birthYear,
        String relationship,
        String occupation,
        boolean under16
) {
}