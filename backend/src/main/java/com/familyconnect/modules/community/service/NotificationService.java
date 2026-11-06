package com.familyconnect.modules.community.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.community.api.CommunityNotificationPort;
import com.familyconnect.modules.community.dto.CommunityDto.PageSuccess;
import com.familyconnect.modules.community.repository.JdbcNotificationStore;
import com.familyconnect.modules.community.repository.JdbcNotificationStore.Notification;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService implements CommunityNotificationPort {
    /** SCRUM-53: mỗi kết quả kiểm duyệt có type riêng để chống trùng nhưng vẫn gửi được khi khôi phục. */
    public enum ModerationType {
        CONTENT_HIDDEN, CONTENT_RESTORED, REPORT_HIDDEN, REPORT_DISMISSED, REPORT_RESTORED
    }
    private final JdbcNotificationStore store;
    private final CommunityPlatformPort platform;
    private final MembershipQuery membership;

    public NotificationService(JdbcNotificationStore store, CommunityPlatformPort platform,
                               MembershipQuery membership) {
        this.store = store;
        this.platform = platform;
        this.membership = membership;
    }

    private Actor actor() {
        Actor current = platform.requireAuthenticatedActor();
        if (current == null || current.userId() == null) throw new ApiException(ErrorCode.AUTH_002);
        if (!current.verified() && current.role() != Role.SYSTEM_ADMIN) throw new ApiException(ErrorCode.PERM_002);
        return current;
    }

    @Transactional(readOnly = true)
    public PageSuccess<Notification> list(Boolean read, int page, int size, String sort) {
        Actor current = actor();
        if (page < 0 || size < 1 || size > 100) throw ApiException.invalidField("page", "Phân trang không hợp lệ");
        String[] parts = (sort == null ? "createdAt,desc" : sort).split(",",-1);
        if (parts.length != 2 || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc")))
            throw ApiException.invalidField("sort", "Sort không hợp lệ");
        String column = switch (parts[0]) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            default -> throw ApiException.invalidField("sort", "Sort không hợp lệ");
        };
        String direction = parts[1].equalsIgnoreCase("asc") ? "ASC" : "DESC";
        String order = column + " " + direction + (column.equals("id") ? "" : ", id " + direction);
        var result = store.list(current.userId(),read,page,size,order);
        return PageSuccess.of(result.items(),page,size,result.total());
    }

    @Transactional
    public Notification markRead(UUID id, Boolean isRead) {
        if (!Boolean.TRUE.equals(isRead)) throw ApiException.invalidField("isRead", "Chỉ hỗ trợ đánh dấu đã đọc");
        Notification found = store.markRead(id,actor().userId());
        if (found == null) throw new ApiException(ErrorCode.NOT_FOUND_001);
        return found;
    }

    @Override
    @Transactional
    public void sendEventReminder(UUID userId, UUID familyId, UUID eventId,
                                  ReminderType type, String title, String content) {
        // Job không có JWT, xác minh lại người nhận ngay trước khi ghi thông báo.
        var current = membership.getMembership(userId);
        if (current.verified() && familyId.equals(current.familyId())) {
            store.send(userId,familyId,"EVENT",eventId,type.name(),title,content,null);
        }
    }

    /** Gửi trong cùng transaction xử lý content_report; ref_id là reportId, tránh trùng khi job/request chạy lại. */
    @Transactional
    public void sendModeration(UUID userId, UUID familyId, UUID reportId,
                               ModerationType type, String title, String content) {
        if (userId == null) return;
        store.send(userId,familyId,"CONTENT_REPORT",reportId,type.name(),title,content,null);
    }

    /** BR-COM-09: thông báo tạo mới gửi tới những người đã xác minh trong phạm vi đọc. */
    @Transactional
    public void announce(UUID familyId, UUID branchId, UUID announcementId,
                         String title, String content, UUID creator) {
        for (UUID user : membership.findVerifiedUserIds(familyId)) {
            var m = membership.getMembership(user);
            Role role = switch (m.role()) {
                case SYSTEM_ADMIN -> Role.SYSTEM_ADMIN;
                case BRANCH_ADMIN -> Role.BRANCH_ADMIN;
                default -> Role.MEMBER;
            };
            if (m.verified() && familyId.equals(m.familyId()) &&
                platform.canRead(new Actor(user,m.familyId(),m.branchId(),role,true),familyId,branchId)) {
                store.send(user,familyId,"ANNOUNCEMENT",announcementId,"ANNOUNCEMENT",title,content,creator);
            }
        }
    }
}
