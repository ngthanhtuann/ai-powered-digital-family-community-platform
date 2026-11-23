package com.familyconnect.modules.community.api;

import java.util.UUID;

/** Events ghi nhắc nhở qua Community, không truy cập trực tiếp bảng notification. */
public interface CommunityNotificationPort {
    enum ReminderType { EVENT_REMINDER_24H, EVENT_REMINDER_1H }

    void sendEventReminder(UUID userId, UUID familyId, UUID eventId,
                           ReminderType type, String title, String content);

    /**
     * BR-COM-15: thông báo tang cho thành viên thuộc các chi branchIds và trưởng họ; branchIds null là cả dòng họ.
     * Không gửi cho deceasedUserId (tài khoản của người mất).
     */
    void sendDeathNotice(UUID familyId, java.util.Collection<UUID> branchIds, UUID personId, UUID deceasedUserId,
                         String title, String content, UUID actorId);

    /** BR-COM-15: báo nhầm thì xóa mềm thông báo tang của hồ sơ. */
    void retractDeathNotice(UUID personId);
}
