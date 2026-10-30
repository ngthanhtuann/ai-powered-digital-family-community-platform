package com.familyconnect.modules.events.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.*;
import com.familyconnect.modules.events.dto.EventDto.*;
import com.familyconnect.modules.events.repository.EventStore;
import com.familyconnect.modules.events.repository.EventStore.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID FAMILY = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-29T10:00:00Z");

    @Mock EventStore store;
    @Mock MembershipQuery membership;
    @Mock ObjectProvider<GenealogyPort> genealogy;
    @Mock UserDirectory users;
    @Mock AuditLogger audit;
    @Mock AiGateway ai;
    private EventService service;

    @BeforeEach void setup() {
        service = new EventService(store,new EventAccess(membership,genealogy,users,audit,ai),
            Clock.fixed(NOW.toInstant(),ZoneOffset.UTC));
        AuthenticatedUser user = new AuthenticatedUser(USER,List.of("MEMBER"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user,null,List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private static MembershipView member(UUID id,boolean verified) {
        return new MembershipView(id,FAMILY,null,EffectiveRole.MEMBER,verified);
    }
    private static Event event(UUID creator,OffsetDateTime start) {
        return new Event(EVENT_ID,FAMILY,null,"Họp mặt gia đình","Buổi gặp mặt thường niên",start,null,
            "Nhà văn hóa",creator,NOW,NOW);
    }
    private void verified() { when(membership.getMembership(USER)).thenReturn(member(USER,true)); }

    @Test void verifiedMemberCanCreateEvent() {
        verified();
        Event saved = event(USER,NOW.plusDays(1));
        when(store.insertEvent(eq(FAMILY),isNull(),eq("Họp mặt gia đình"),eq("Buổi gặp mặt thường niên"),
            eq(saved.startAt()),isNull(),eq("Nhà văn hóa"),eq(USER))).thenReturn(saved);
        when(store.counts(List.of(EVENT_ID))).thenReturn(Map.of(EVENT_ID,new RsvpCounts(0,0,0)));
        EventView result = service.create(null,new CreateEvent("Họp mặt gia đình","Buổi gặp mặt thường niên",
            saved.startAt(),null,"Nhà văn hóa",null));
        assertThat(result.id()).isEqualTo(EVENT_ID);
        verify(ai).upsertSource(AiGateway.SourceType.EVENT,EVENT_ID,FAMILY,null,
            "Họp mặt gia đình Buổi gặp mặt thường niên");
    }

    @Test void unverifiedCannotCreateEvent() {
        when(membership.getMembership(USER)).thenReturn(member(USER,false));
        assertThatThrownBy(() -> service.create(null,new CreateEvent("Họp mặt gia đình",null,NOW.plusDays(1),null,null,null)))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.PERM_002));
        verifyNoInteractions(store);
    }

    @Test void invalidEndTimeIsRejected() {
        verified();
        assertThatThrownBy(() -> service.create(null,new CreateEvent("Họp mặt gia đình",null,NOW.plusDays(1),NOW,null,null)))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.VALID_001));
        verifyNoInteractions(store);
    }

    @Test void otherMemberCannotUpdateOrDelete() {
        verified();
        when(store.findEvent(EVENT_ID)).thenReturn(Optional.of(event(OTHER,NOW.plusDays(1))));
        assertThatThrownBy(() -> service.update(EVENT_ID,new UpdateEvent()))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.PERM_001));
        assertThatThrownBy(() -> service.delete(EVENT_ID))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.PERM_001));
        verify(store,never()).deleteEvent(any(),any());
    }

    @Test void rsvpBeforeStartAndCountsAreCorrect() {
        verified();
        when(store.findEvent(EVENT_ID)).thenReturn(Optional.of(event(USER,NOW.plusDays(1))));
        UUID participantId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        Participant saved = new Participant(participantId,EVENT_ID,USER,RsvpStatus.GOING,NOW,NOW);
        when(store.upsertRsvp(EVENT_ID,USER,RsvpStatus.GOING,USER)).thenReturn(saved);
        when(users.getFullNames(any())).thenReturn(Map.of(USER,"Nguyễn Văn A"));
        when(store.myStatuses(List.of(EVENT_ID),USER)).thenReturn(Map.of(EVENT_ID,RsvpStatus.GOING));
        when(store.counts(List.of(EVENT_ID))).thenReturn(Map.of(EVENT_ID,new RsvpCounts(2,1,3)));
        assertThat(service.rsvp(EVENT_ID,new RsvpRequest(RsvpStatus.GOING)).displayName()).isEqualTo("Nguyễn Văn A");
        EventView details = service.get(EVENT_ID);
        assertThat(details.myRsvpStatus()).isEqualTo(RsvpStatus.GOING);
        assertThat(details.rsvpCounts()).isEqualTo(new RsvpCounts(2,1,3));
    }

    @Test void rsvpAtStartReturnsEvt001() {
        verified();
        when(store.findEvent(EVENT_ID)).thenReturn(Optional.of(event(USER,NOW)));
        assertThatThrownBy(() -> service.rsvp(EVENT_ID,new RsvpRequest(RsvpStatus.MAYBE)))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.EVT_001));
        verify(store,never()).upsertRsvp(any(),any(),any(),any());
    }

    @Test void duplicateParticipantIsRejected() {
        verified();
        when(membership.getMembership(OTHER)).thenReturn(member(OTHER,true));
        when(store.findEvent(EVENT_ID)).thenReturn(Optional.of(event(USER,NOW.plusDays(1))));
        when(store.insertParticipant(EVENT_ID,OTHER,RsvpStatus.MAYBE,USER))
            .thenThrow(new DuplicateKeyException("uq_event_participant_active"));
        assertThatThrownBy(() -> service.addParticipant(EVENT_ID,new ParticipantRequest(OTHER,null)))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT_001));
    }
}
