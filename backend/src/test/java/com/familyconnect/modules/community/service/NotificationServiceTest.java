package com.familyconnect.modules.community.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.community.api.CommunityNotificationPort.ReminderType;
import com.familyconnect.modules.community.service.NotificationService.ModerationType;
import com.familyconnect.modules.community.repository.JdbcNotificationStore;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();
    private static final UUID EVENT = UUID.randomUUID();

    @Mock JdbcNotificationStore store;
    @Mock CommunityPlatformPort platform;
    @Mock MembershipQuery membership;

    private NotificationService service() { return new NotificationService(store,platform,membership); }

    @Test void cannotMarkAnotherUsersNotificationRead() {
        when(platform.requireAuthenticatedActor()).thenReturn(new Actor(USER,FAMILY,null,Role.MEMBER,true));
        UUID otherNotification = UUID.randomUUID();
        when(store.markRead(otherNotification,USER)).thenReturn(null);
        assertThatThrownBy(() -> service().markRead(otherNotification,true))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND_001));
        verify(store).markRead(otherNotification,USER);
    }

    @Test void rejectsUnreadMutationAndUnverifiedMember() {
        assertThatThrownBy(() -> service().markRead(UUID.randomUUID(),false))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(store);
        when(platform.requireAuthenticatedActor()).thenReturn(new Actor(USER,FAMILY,null,Role.MEMBER,false));
        assertThatThrownBy(() -> service().list(null,0,20,null))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.PERM_002));
    }

    @Test void reminderOnlyPersistsForVerifiedFamilyMember() {
        when(membership.getMembership(USER)).thenReturn(
            new MembershipView(USER,FAMILY,null,EffectiveRole.MEMBER,true));
        service().sendEventReminder(USER,FAMILY,EVENT,ReminderType.EVENT_REMINDER_24H,"Nhắc","Nội dung");
        verify(store).send(USER,FAMILY,"EVENT",EVENT,"EVENT_REMINDER_24H","Nhắc","Nội dung",null);
        reset(store,membership);
        when(membership.getMembership(USER)).thenReturn(
            new MembershipView(USER,UUID.randomUUID(),null,EffectiveRole.MEMBER,true));
        service().sendEventReminder(USER,FAMILY,EVENT,ReminderType.EVENT_REMINDER_1H,"Nhắc","Nội dung");
        verifyNoInteractions(store);
    }

    @Test void moderationNotificationsHaveDistinctTypesAndReportReferences() {
        UUID reportId = UUID.randomUUID();
        service().sendModeration(USER,FAMILY,reportId,ModerationType.CONTENT_HIDDEN,"Đã ẩn","Nội dung");
        service().sendModeration(USER,FAMILY,reportId,ModerationType.CONTENT_RESTORED,"Khôi phục","Nội dung");
        verify(store).send(USER,FAMILY,"CONTENT_REPORT",reportId,"CONTENT_HIDDEN","Đã ẩn","Nội dung",null);
        verify(store).send(USER,FAMILY,"CONTENT_REPORT",reportId,"CONTENT_RESTORED","Khôi phục","Nội dung",null);
    }
}
