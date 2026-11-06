package com.familyconnect.modules.community.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.ContentModerationPort;
import com.familyconnect.modules.community.dto.CommunityDto.ReportInput;
import com.familyconnect.modules.community.dto.CommunityDto.ReportView;
import com.familyconnect.modules.community.repository.JdbcContentReportStore;
import com.familyconnect.modules.community.repository.JdbcContentReportStore.Target;
import com.familyconnect.modules.community.repository.JdbcContentReportStore.Reporter;
import com.familyconnect.modules.community.service.NotificationService.ModerationType;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Báo cáo nội dung vi phạm (BR-COM-12): thành viên báo cáo; quản trị xử lý qua {@link ContentModerationPort}
 * (UC-AUTH-09, BR-AUTH-15). Ẩn bằng hidden_at, không xóa cứng; bảng tin và danh sách đã lọc hidden_at.
 */
@Service
public class ContentReportService implements ContentModerationPort {

    private static final Logger log = LoggerFactory.getLogger(ContentReportService.class);
    static final int SNIPPET_MAX = 200;
    /** conventions.md mục 7.1: tối đa 20 báo cáo mỗi giờ mỗi người. */
    static final int REPORTS_PER_HOUR = 20;

    private final JdbcContentReportStore store;
    private final CommunityPlatformPort platform;
    private final AuditLogger auditLogger;
    private final AiGateway aiGateway;
    private final NotificationService notifications;

    public ContentReportService(JdbcContentReportStore store, CommunityPlatformPort platform, AuditLogger auditLogger,
                                AiGateway aiGateway, NotificationService notifications) {
        this.store = store;
        this.platform = platform;
        this.auditLogger = auditLogger;
        this.aiGateway = aiGateway;
        this.notifications = notifications;
    }

    /** POST /content-reports: x-roles MEMBER, BRANCH_ADMIN; chỉ nội dung mình xem được; mỗi người một lần. */
    @Transactional
    public ReportView report(ReportInput input) {
        Actor actor = platform.requireAuthenticatedActor();
        if (actor.role() == Role.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (!actor.verified()) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        String reason = input.reason().trim();
        if (reason.isEmpty()) {
            throw ApiException.invalidField("reason", "Cần nhập lý do báo cáo");
        }
        Target target = store.findTarget(input.targetType(), input.targetId(), true)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        platform.requireCanRead(actor, target.familyId(), target.branchId());
        long wait = store.reportWaitSeconds(actor.userId(), REPORTS_PER_HOUR);
        if (wait > 0) {
            throw ApiException.tooManyRequests(wait);
        }
        if (store.exists(input.targetType(), input.targetId(), actor.userId())) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Bạn đã báo cáo nội dung này");
        }
        String text = target.text() == null ? "" : target.text().strip();
        String snippet = text.length() > SNIPPET_MAX ? text.substring(0, SNIPPET_MAX - 3) + "..." : text;
        try {
            // Hai request cùng lúc: unique index chặn bản thứ hai.
            return ReportView.of(store.insert(target.familyId(), input.targetType(), input.targetId(), actor.userId(),
                    reason, snippet));
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.CONFLICT_001, "Bạn đã báo cáo nội dung này");
        }
    }

    // ---- ContentModerationPort: module admin đã kiểm SYSTEM_ADMIN ----

    @Override
    @Transactional(readOnly = true)
    public Page<ContentReportView> findReports(ReportStatus status, ContentType type, Pageable pageable) {
        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        boolean ascending = order != null && order.isAscending();
        JdbcContentReportStore.Page found = store.find(status, type, ascending, pageable.getPageSize(),
                pageable.getOffset());
        List<ContentReportView> items = found.items().stream().map(this::withOwner).toList();
        return new PageImpl<>(items, pageable, found.total());
    }

    @Override
    @Transactional
    public ContentReportView hideContent(UUID reportId, UUID adminUserId) {
        ContentReportView report = locked(reportId, ReportStatus.PENDING);
        store.setHidden(report.targetType(), report.targetId(), true, adminUserId);
        List<Reporter> reporters = store.moveReports(report.targetType(), report.targetId(), ReportStatus.PENDING, ReportStatus.HIDDEN,
                adminUserId);
        int count = reporters.size();
        audit("CONTENT_HIDDEN", report, count);
        notifyOwner(report,ModerationType.CONTENT_HIDDEN,"Nội dung của bạn đã bị ẩn",
            "Nội dung đã bị quản trị ẩn sau khi xử lý báo cáo.");
        notifyReporters(report,reporters,ModerationType.REPORT_HIDDEN,
            "Báo cáo của bạn đã được xử lý","Nội dung bạn báo cáo đã bị ẩn.");
        reindexAfterCommit(report, false);
        return result(reportId, count);
    }

    @Override
    @Transactional
    public ContentReportView dismissReport(UUID reportId, UUID adminUserId) {
        ContentReportView report = locked(reportId, ReportStatus.PENDING);
        List<Reporter> reporters = store.moveReports(report.targetType(), report.targetId(), ReportStatus.PENDING,
                ReportStatus.DISMISSED, adminUserId);
        int count = reporters.size();
        audit("REPORT_DISMISSED", report, count);
        notifyReporters(report,reporters,ModerationType.REPORT_DISMISSED,
            "Báo cáo của bạn đã được xử lý","Báo cáo đã được xem xét và bỏ qua.");
        return result(reportId, count);
    }

    @Override
    @Transactional
    public ContentReportView restoreContent(UUID reportId, UUID adminUserId) {
        ContentReportView report = locked(reportId, ReportStatus.HIDDEN);
        store.setHidden(report.targetType(), report.targetId(), false, adminUserId);
        List<Reporter> reporters = store.moveReports(report.targetType(), report.targetId(), ReportStatus.HIDDEN,
                ReportStatus.DISMISSED, adminUserId);
        int count = reporters.size();
        audit("CONTENT_RESTORED", report, count);
        notifyOwner(report,ModerationType.CONTENT_RESTORED,"Nội dung của bạn đã được khôi phục",
            "Nội dung được khôi phục sau khi quản trị xem xét lại.");
        notifyReporters(report,reporters,ModerationType.REPORT_RESTORED,
            "Nội dung đã được khôi phục","Nội dung trong báo cáo của bạn đã được khôi phục sau khi xem xét.");
        reindexAfterCommit(report, true);
        return result(reportId, count);
    }

    /** 404 nếu không có; 409 ADM_002 nếu không ở trạng thái cần cho thao tác. */
    private ContentReportView locked(UUID reportId, ReportStatus required) {
        ContentReportView report = store.lock(reportId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (report.status() != required) {
            throw new ApiException(ErrorCode.ADM_002);
        }
        return report;
    }

    private ContentReportView result(UUID reportId, int count) {
        ContentReportView v = store.find(reportId).orElseThrow();
        return withOwner(new ContentReportView(v.id(), v.targetType(), v.targetId(), v.familyId(), v.reporterId(),
                v.reason(), v.contentSnippet(), v.status(), v.createdAt(), v.updatedAt(), null, Math.max(count, 1)));
    }

    // shortcut: mỗi dòng một truy vấn tìm chủ nội dung (tối đa 100/trang); gom theo loại nếu hàng đợi lớn.
    private ContentReportView withOwner(ContentReportView v) {
        return new ContentReportView(v.id(), v.targetType(), v.targetId(), v.familyId(), v.reporterId(), v.reason(),
                v.contentSnippet(), v.status(), v.createdAt(), v.updatedAt(),
                store.ownerOf(v.targetType(), v.targetId()).orElse(null), v.reportCount());
    }

    /** auth.md mục 6.4: ghi người xử lý (lấy từ ngữ cảnh request), chủ nội dung và số báo cáo đã đổi. */
    private void audit(String action, ContentReportView report, int reportCount) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("reportId", report.id());
        detail.put("reportCount", reportCount);
        store.ownerOf(report.targetType(), report.targetId()).ifPresent(owner -> detail.put("contentOwnerId", owner));
        auditLogger.log(action, report.targetType().name(), report.targetId(), report.familyId(), detail);
    }

    private void notifyOwner(ContentReportView report, ModerationType type, String title, String body) {
        store.ownerOf(report.targetType(),report.targetId()).ifPresent(owner ->
            notifications.sendModeration(owner,report.familyId(),report.id(),type,title,body));
    }

    private void notifyReporters(ContentReportView report, List<Reporter> reporters,
                                 ModerationType type, String title, String body) {
        for (Reporter reporter : reporters) {
            notifications.sendModeration(reporter.userId(),report.familyId(),reporter.reportId(),type,title,body);
        }
    }

    /** BR-AI-15: ẩn thì gỡ khỏi chỉ mục AI, khôi phục thì thêm lại. Gọi sau commit, lỗi AI không hỏng thao tác. */
    private void reindexAfterCommit(ContentReportView report, boolean restored) {
        AiGateway.SourceType source = switch (report.targetType()) {
            case POST -> AiGateway.SourceType.POST;
            case EVENT -> AiGateway.SourceType.EVENT;
            default -> null;
        };
        if (source == null) {
            return;
        }
        // Đọc ngay trong giao dịch; nội dung đã xóa trong lúc chờ thì chỉ gỡ.
        Target target = restored ? store.findTarget(report.targetType(), report.targetId(), true).orElse(null) : null;
        Runnable work = () -> {
            try {
                if (target == null) {
                    aiGateway.removeSource(source, report.targetId());
                } else {
                    aiGateway.upsertSource(source, report.targetId(), target.familyId(), target.branchId(), target.text());
                }
            } catch (RuntimeException ex) {
                log.warn("Không cập nhật được chỉ mục AI cho nội dung {}", report.targetId(), ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }
}
