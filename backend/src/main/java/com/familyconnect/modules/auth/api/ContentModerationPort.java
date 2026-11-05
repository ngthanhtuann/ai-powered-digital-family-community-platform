package com.familyconnect.modules.auth.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cổng ra do auth/admin khai báo, module Community cài đặt (auth.md mục 6.3, BR-AUTH-15, BR-COM-12).
 * Admin không đọc bảng content_report trực tiếp (quyết định 9). Quyết định áp cho cả nội dung: mọi báo cáo cùng
 * nội dung đổi trạng thái theo. Community ghi nhật ký CONTENT_HIDDEN, REPORT_DISMISSED, CONTENT_RESTORED vì chỉ
 * module đó biết chủ nội dung.
 */
public interface ContentModerationPort {

    enum ContentType { POST, COMMENT, EVENT, ANNOUNCEMENT }

    enum ReportStatus { PENDING, HIDDEN, DISMISSED }

    /** Một dòng hàng đợi: báo cáo sớm nhất của nội dung ở trạng thái đó, kèm số lượt báo cáo và chủ nội dung. */
    record ContentReportView(UUID id, ContentType targetType, UUID targetId, UUID familyId, UUID reporterId,
                             String reason, String contentSnippet, ReportStatus status,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt,
                             UUID contentOwnerId, int reportCount) {
    }

    /** type null là mọi loại; mỗi nội dung một dòng; pageable chỉ sắp theo createdAt. */
    Page<ContentReportView> findReports(ReportStatus status, ContentType type, Pageable pageable);

    /** Ẩn nội dung (đặt hidden_at), mọi báo cáo PENDING của nội dung sang HIDDEN. 404, 409 ADM_002. */
    ContentReportView hideContent(UUID reportId, UUID adminUserId);

    /** Mọi báo cáo PENDING của nội dung sang DISMISSED. 404, 409 ADM_002. */
    ContentReportView dismissReport(UUID reportId, UUID adminUserId);

    /** Bỏ ẩn nội dung; báo cáo HIDDEN của nội dung sang DISMISSED. 404; 409 ADM_002 nếu báo cáo không HIDDEN. */
    ContentReportView restoreContent(UUID reportId, UUID adminUserId);
}
