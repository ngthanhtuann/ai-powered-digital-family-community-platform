package com.familyconnect.modules.ai.api;

import java.util.UUID;

/**
 * Module khác báo nguồn dữ liệu thay đổi để module AI cập nhật chỉ mục (ai.md mục 6.1).
 * Gọi sau khi giao dịch của module nguồn commit; lỗi AI không làm hỏng thao tác nguồn.
 */
public interface AiGateway {

    /** Tạo hoặc sửa nguồn. {@code content} do module nguồn dựng, đã lọc theo BR-AI-07, BR-AI-14; branchId null là cả gia đình. */
    void upsertSource(SourceType type, UUID sourceId, UUID familyId, UUID branchId, String content);

    /** Xóa mềm, ẩn hoặc nguồn không còn hiển thị. */
    void removeSource(SourceType type, UUID sourceId);

    enum SourceType { PERSON, POST, STORY, DOCUMENT, EVENT }
}
