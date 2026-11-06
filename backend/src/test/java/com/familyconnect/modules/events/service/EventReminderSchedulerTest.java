package com.familyconnect.modules.events.service;

import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.community.api.CommunityNotificationPort;
import com.familyconnect.modules.community.api.CommunityNotificationPort.ReminderType;
import com.familyconnect.modules.events.repository.JdbcReminderStore;
import com.familyconnect.modules.events.repository.JdbcReminderStore.Candidate;
import com.familyconnect.modules.events.service.EventAccess.Actor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mốc thời gian tính bằng Instant (UTC); cả 2 lần nhắc là 2 type khác nhau. */
@ExtendWith(MockitoExtension.class)
class EventReminderSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID EVENT = UUID.randomUUID();

    @Mock JdbcReminderStore events;
    @Mock MembershipQuery membership;
    @Mock EventAccess access;
    @Mock CommunityNotificationPort notifications;

    private EventReminderScheduler scheduler() {
        return new EventReminderScheduler(events,membership,access,notifications,
            Clock.fixed(NOW,ZoneOffset.UTC));
    }

    private Candidate event(int hours) {
        return new Candidate(EVENT,FAMILY,null,USER,"Họp mặt",
            OffsetDateTime.ofInstant(NOW.plusSeconds(hours*3600L),ZoneOffset.UTC));
    }

    private void verified() {
        when(membership.getMembership(USER)).thenReturn(
            new MembershipView(USER,FAMILY,null,EffectiveRole.MEMBER,true));
        when(access.canRead(any(Actor.class),eq(FAMILY),isNull())).thenReturn(true);
    }

    @Test void sends24HourReminderForVerifiedGoingOrMaybeCandidates() {
        when(events.due(NOW,24)).thenReturn(List.of(event(24)));
        verified();
        scheduler().runAt(NOW);
        verify(notifications).sendEventReminder(eq(USER),eq(FAMILY),eq(EVENT),
            eq(ReminderType.EVENT_REMINDER_24H),contains("Họp mặt"),anyString());
        verify(notifications,never()).sendEventReminder(any(),any(),any(),
            eq(ReminderType.EVENT_REMINDER_1H),anyString(),anyString());
    }

    @Test void sendsBothMilestonesWhenOneHourRemainsAnd24HourWasMissed() {
        when(events.due(NOW,24)).thenReturn(List.of(event(1)));
        when(events.due(NOW,1)).thenReturn(List.of(event(1)));
        verified();
        scheduler().runAt(NOW);
        verify(notifications).sendEventReminder(eq(USER),eq(FAMILY),eq(EVENT),
            eq(ReminderType.EVENT_REMINDER_24H),anyString(),anyString());
        verify(notifications).sendEventReminder(eq(USER),eq(FAMILY),eq(EVENT),
            eq(ReminderType.EVENT_REMINDER_1H),anyString(),anyString());
    }

    @Test void doesNotSendToUnverifiedOrWrongFamilyOrNoAccess() {
        when(events.due(NOW,24)).thenReturn(List.of(event(24)));
        when(membership.getMembership(USER)).thenReturn(
            new MembershipView(USER,FAMILY,null,EffectiveRole.MEMBER,false));
        scheduler().runAt(NOW);
        verifyNoInteractions(notifications);

        reset(membership);
        when(membership.getMembership(USER)).thenReturn(
            new MembershipView(USER,UUID.randomUUID(),null,EffectiveRole.MEMBER,true));
        scheduler().runAt(NOW);
        verifyNoInteractions(notifications);

        reset(membership);
        when(membership.getMembership(USER)).thenReturn(
            new MembershipView(USER,FAMILY,null,EffectiveRole.MEMBER,true));
        when(access.canRead(any(Actor.class),eq(FAMILY),isNull())).thenReturn(false);
        scheduler().runAt(NOW);
        verifyNoInteractions(notifications);
    }
}
