package com.familyconnect.modules.ai.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(List<Item> recommendations) {

    /** reason chỉ nêu dữ kiện có thật: điểm gần nhau tính từ embedding đã lưu (UC-AI-05 bước 5). */
    public record Item(String sourceType, UUID sourceId, String title, String reason, String snippet,
                       double score, String path) { }
}
