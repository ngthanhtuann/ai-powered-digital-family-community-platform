package com.familyconnect.modules.events.service;

import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.community.api.CommunityNotificationPort;
import com.familyconnect.modules.community.api.CommunityNotificationPort.ReminderType;
import com.familyconnect.modules.events.repository.JdbcReminderStore;
import com.familyconnect.modules.events.repository.JdbcReminderStore.Candidate;
import com.familyconnect.modules.events.service.EventAccess.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** [5-04] BR-EVT-05: in-app reminders, dùng UTC; khóa unique trong notification chống gửi trùng. */
@Component
public class EventReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(EventReminderScheduler.class);
    private final JdbcReminderStore events;
    private final MembershipQuery membership;
    private final EventAccess access;
    private final CommunityNotificationPort notifications;
    private final Clock clock;

    public EventReminderScheduler(JdbcReminderStore events, MembershipQuery membership, EventAccess access,
                                  CommunityNotificationPort notifications, Clock clock) {
        this.events = events;
        this.membership = membership;
        this.access = access;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString="${app.events.reminder-poll-ms:60000}")
    public void scheduled() { runAt(clock.instant()); }

    /** Package-visible để test các mốc UTC mà không phải chờ job chạy thật. */
    void runAt(Instant now) {
        send(now,24,ReminderType.EVENT_REMINDER_24H);
        send(now,1,ReminderType.EVENT_REMINDER_1H);
    }

    private void send(Instant now, int hours, ReminderType type) {
        for (Candidate event : events.due(now,hours)) {
            try {
                var member = membership.getMembership(event.userId());
                if (!member.verified() || !event.familyId().equals(member.familyId())) continue;
                Actor actor = new Actor(event.userId(),member.familyId(),member.branchId(),member.role());
                if (!access.canRead(actor,event.familyId(),event.branchId())) continue;
                String label = hours == 24 ? "24 giờ" : "1 giờ";
                String title = "Nhắc sự kiện: " + event.eventTitle();
                if (title.length() > 255) title = title.substring(0,255);
                notifications.sendEventReminder(event.userId(),event.familyId(),event.eventId(),type,
                    title,
                    "Sự kiện " + event.eventTitle() + " sẽ bắt đầu trong " + label + " hoặc sớm hơn.");
            } catch (RuntimeException ex) {
                // Một thành viên bị khóa/xóa không được làm gián đoạn nhắc nhở của người khác.
                log.warn("Không tạo được nhắc nhở {} cho sự kiện {} / người {}",
                    type,event.eventId(),event.userId(),ex);
            }
        }
    }
}
