package com.familyconnect.modules.community.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentReportView;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentType;
import com.familyconnect.modules.auth.api.ContentModerationPort.ReportStatus;
import com.familyconnect.modules.community.repository.JdbcContentReportStore;
import com.familyconnect.modules.community.repository.JdbcContentReportStore.Reporter;
import com.familyconnect.modules.community.service.NotificationService.ModerationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SCRUM-53: gửi đến chủ nội dung và từng reporter khi admin xử lý hàng đợi gộp. */
@ExtendWith(MockitoExtension.class)
class ContentReportNotificationsTest {
    private static final UUID REPORT = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID REPORTER_1 = UUID.randomUUID();
    private static final UUID REPORTER_2 = UUID.randomUUID();
    private static final UUID REPORT_2 = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-05T00:00:00Z");

    @Mock JdbcContentReportStore store;
    @Mock CommunityPlatformPort platform;
    @Mock AuditLogger audit;
    @Mock AiGateway ai;
    @Mock NotificationService notifications;

    private ContentReportService service() {
        return new ContentReportService(store,platform,audit,ai,notifications);
    }

    private ContentReportView report(ReportStatus status) {
        return new ContentReportView(REPORT,ContentType.ANNOUNCEMENT,TARGET,FAMILY,REPORTER_1,
            "Lý do","Trích đoạn",status,NOW,NOW,OWNER,2);
    }

    private List<Reporter> reporters() {
        return List.of(new Reporter(REPORT,REPORTER_1),new Reporter(REPORT_2,REPORTER_2));
    }

    private void stub(ReportStatus from, ReportStatus to) {
        when(store.lock(REPORT)).thenReturn(Optional.of(report(from)));
        when(store.moveReports(ContentType.ANNOUNCEMENT,TARGET,from,to,ADMIN)).thenReturn(reporters());
        when(store.ownerOf(ContentType.ANNOUNCEMENT,TARGET)).thenReturn(Optional.of(OWNER));
        when(store.find(REPORT)).thenReturn(Optional.of(report(to)));
    }

    @Test void hidingSendsToOwnerAndEveryReporter() {
        stub(ReportStatus.PENDING,ReportStatus.HIDDEN);
        service().hideContent(REPORT,ADMIN);
        verify(store).setHidden(ContentType.ANNOUNCEMENT,TARGET,true,ADMIN);
        verify(notifications).sendModeration(eq(OWNER),eq(FAMILY),eq(REPORT),
            eq(ModerationType.CONTENT_HIDDEN),anyString(),anyString());
        verify(notifications).sendModeration(eq(REPORTER_1),eq(FAMILY),eq(REPORT),
            eq(ModerationType.REPORT_HIDDEN),anyString(),anyString());
        verify(notifications).sendModeration(eq(REPORTER_2),eq(FAMILY),eq(REPORT_2),
            eq(ModerationType.REPORT_HIDDEN),anyString(),anyString());
    }

    @Test void dismissingInformsReportersOnly() {
        stub(ReportStatus.PENDING,ReportStatus.DISMISSED);
        service().dismissReport(REPORT,ADMIN);
        verify(notifications,never()).sendModeration(eq(OWNER),eq(FAMILY),any(),
            eq(ModerationType.CONTENT_HIDDEN),anyString(),anyString());
        verify(notifications).sendModeration(eq(REPORTER_1),eq(FAMILY),eq(REPORT),
            eq(ModerationType.REPORT_DISMISSED),anyString(),anyString());
        verify(notifications).sendModeration(eq(REPORTER_2),eq(FAMILY),eq(REPORT_2),
            eq(ModerationType.REPORT_DISMISSED),anyString(),anyString());
    }

    @Test void restoringInformsOwnerAndReporters() {
        stub(ReportStatus.HIDDEN,ReportStatus.DISMISSED);
        service().restoreContent(REPORT,ADMIN);
        verify(store).setHidden(ContentType.ANNOUNCEMENT,TARGET,false,ADMIN);
        verify(notifications).sendModeration(eq(OWNER),eq(FAMILY),eq(REPORT),
            eq(ModerationType.CONTENT_RESTORED),anyString(),anyString());
        verify(notifications).sendModeration(eq(REPORTER_1),eq(FAMILY),eq(REPORT),
            eq(ModerationType.REPORT_RESTORED),anyString(),anyString());
    }
}
