package com.familyconnect.modules.heritage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class HeritageRequests {

    private HeritageRequests() {
    }

    public record CreateDocument(
            @NotBlank @Size(max = 200) String title,
            String description,
            @Size(max = 50) String category,
            @NotNull UUID mediaId
    ) {
    }

    public record CreateStory(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content,
            UUID relatedPersonId
    ) {
    }
}
