package com.familyconnect.modules.ai.api;

import java.util.Set;
import java.util.UUID;

/**
 * Module sở hữu nội dung cấp văn bản cho AI tóm tắt (ai.md UC-AI-04 bước 2, 3); module ai không gọi service
 * hay đọc bảng của module khác (BR-AI-05).
 */
public interface SummarySource {

    /** Loại nguồn module này phục vụ, ví dụ POST hoặc STORY, DOCUMENT. */
    Set<String> sourceTypes();

    /**
     * Kiểm quyền đọc của người đang gọi rồi trả văn bản đã lọc theo privacy.md mục 6. Ném 403/404 như API đọc
     * của chính module đó.
     */
    SummaryText readForSummary(String sourceType, UUID sourceId);

    record SummaryText(UUID familyId, String text) {
    }
}
