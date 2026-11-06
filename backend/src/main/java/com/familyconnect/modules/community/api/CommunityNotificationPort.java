package com.familyconnect.modules.community.api;

import java.util.UUID;

/** Events ghi nhắc nhở qua Community, không truy cập trực tiếp bảng notification. */
public interface CommunityNotificationPort {
    enum ReminderType { EVENT_REMINDER_24H, EVENT_REMINDER_1H }

    void sendEventReminder(UUID userId, UUID familyId, UUID eventId,
                           ReminderType type, String title, String content);
}
