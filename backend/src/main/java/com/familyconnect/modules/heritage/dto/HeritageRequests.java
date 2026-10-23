package com.familyconnect.modules.heritage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class HeritageRequests {

    private HeritageRequests() {
    }

    public record CreateDocument(
            @NotBlank(message = "Tiêu đề không được để trống")
            @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự") String title,
            String description,
            @Size(max = 50, message = "Danh mục tối đa 50 ký tự") String category,
            @NotNull(message = "Chưa chọn tệp tài liệu") UUID mediaId
    ) {
    }

    public record CreateStory(
            @NotBlank(message = "Tiêu đề không được để trống")
            @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự") String title,
            @NotBlank(message = "Nội dung không được để trống") String content,
            UUID relatedPersonId
    ) {
    }
}
