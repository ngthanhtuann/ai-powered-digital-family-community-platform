package com.familyconnect.modules.ai.service;

import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.RelationshipExplanationRequest;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.PathStep;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.RelationshipResult;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelationshipExplanationServiceTest {
    @Test void tenSyntheticPairsKeepAlgorithmRelationAsGroundTruth() {
        GenealogyQuery genealogy = mock(GenealogyQuery.class);
        MembershipQuery membership = mock(MembershipQuery.class);
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        AiSearchRateLimiter limiter = mock(AiSearchRateLimiter.class);
        UUID user = UUID.randomUUID(), family = UUID.randomUUID();
        when(genealogy.findPersonFamilyId(any())).thenReturn(java.util.Optional.of(family));
        when(genealogy.findMinorPersonIds(any(), any())).thenReturn(List.of());
        when(model.isConfigured()).thenReturn(true);
        when(model.answer(anyString())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            assertTrue(prompt.contains("relation=relation-"));
            return "Explanation grounded in the supplied algorithm result.";
        });
        RelationshipExplanationService service = new RelationshipExplanationService(genealogy, membership, model,
                limiter, Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneOffset.UTC), true);
        AuthenticatedUser actor = new AuthenticatedUser(user, List.of("MEMBER"));
        for (int i = 0; i < 10; i++) {
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            String relation = "relation-" + i;
            when(genealogy.relationship(a, b)).thenReturn(new RelationshipResult(true, relation, 1, 2,
                    List.of(new PathStep(a, "Synthetic A"), new PathStep(b, "Synthetic B"))));
            var response = service.explain(actor, new RelationshipExplanationRequest(a, b));
            assertEquals(relation, response.relation());
            assertEquals("Explanation grounded in the supplied algorithm result.", response.explanation());
            verify(genealogy).relationship(a, b);
        }
    }

    @Test void minorNamesAreReplacedBeforePromptAndReturnedPath() {
        GenealogyQuery genealogy = mock(GenealogyQuery.class);
        MembershipQuery membership = mock(MembershipQuery.class);
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        UUID user = UUID.randomUUID(), family = UUID.randomUUID(), a = UUID.randomUUID(), minor = UUID.randomUUID(), b = UUID.randomUUID();
        when(genealogy.findPersonFamilyId(any())).thenReturn(java.util.Optional.of(family));
        when(genealogy.findMinorPersonIds(any(), any())).thenReturn(List.of(minor));
        when(genealogy.relationship(a, b)).thenReturn(new RelationshipResult(true, "cousin", 2, 2,
                List.of(new PathStep(a, "Adult"), new PathStep(minor, "Child Secret Name"), new PathStep(b, "Adult B"))));
        when(model.isConfigured()).thenReturn(true);
        when(model.answer(anyString())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            assertFalse(prompt.contains("Child Secret Name"));
            assertTrue(prompt.contains("một thành viên"));
            return "Explanation";
        });
        var service = new RelationshipExplanationService(genealogy, membership, model, mock(AiSearchRateLimiter.class),
                Clock.systemUTC(), true);
        var response = service.explain(new AuthenticatedUser(user, List.of("MEMBER")),
                new RelationshipExplanationRequest(a, b));
        assertEquals("một thành viên", response.path().get(1).fullName());
    }

    @Test void unsupportedRelationReturnsFixedTextWithoutCallingModel() {
        GenealogyQuery genealogy = mock(GenealogyQuery.class);
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        UUID family = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        when(genealogy.findPersonFamilyId(any())).thenReturn(java.util.Optional.of(family));
        when(genealogy.findMinorPersonIds(any(), any())).thenReturn(List.of());
        when(genealogy.relationship(a, b)).thenReturn(new RelationshipResult(false, null, null, null, List.of()));
        var service = new RelationshipExplanationService(genealogy, mock(MembershipQuery.class), model,
                mock(AiSearchRateLimiter.class), Clock.systemUTC(), true);
        var response = service.explain(new AuthenticatedUser(UUID.randomUUID(), List.of("MEMBER")),
                new RelationshipExplanationRequest(a, b));
        assertFalse(response.found());
        assertEquals(RelationshipExplanationService.NOT_FOUND_TEXT, response.explanation());
        verify(model, never()).answer(anyString());
    }

    @Test void unverifiedUserIsRejectedBeforeAnyPersonLookup() {
        GenealogyQuery genealogy = mock(GenealogyQuery.class);
        MembershipQuery membership = mock(MembershipQuery.class);
        UUID user = UUID.randomUUID();
        doThrow(new com.familyconnect.common.exception.ApiException(
                com.familyconnect.common.exception.ErrorCode.PERM_002)).when(membership).requireVerified(user);
        var service = new RelationshipExplanationService(genealogy, membership, mock(ChatLanguageModel.class),
                mock(AiSearchRateLimiter.class), Clock.systemUTC(), true);
        var error = assertThrows(com.familyconnect.common.exception.ApiException.class, () -> service.explain(
                new AuthenticatedUser(user, List.of("MEMBER")),
                new RelationshipExplanationRequest(UUID.randomUUID(), UUID.randomUUID())));
        assertEquals(com.familyconnect.common.exception.ErrorCode.PERM_002, error.code());
        verify(genealogy, never()).findPersonFamilyId(any());
    }
}
