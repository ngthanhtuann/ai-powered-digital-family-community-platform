package com.familyconnect.modules.ai.service;

import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.RelationshipExplanationRequest;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import com.familyconnect.modules.genealogy.dto.RelationshipResponse.PathPerson;
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
            when(genealogy.relationship(a, b)).thenReturn(new RelationshipResponse(true, relation,
                    "algorithm description", null, 1, 2,
                    List.of(new PathPerson(a, "Synthetic A"), new PathPerson(b, "Synthetic B"))));
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
        when(genealogy.relationship(a, b)).thenReturn(new RelationshipResponse(true, "cousin", "", null, 2, 2,
                List.of(new PathPerson(a, "Adult"), new PathPerson(minor, "Child Secret Name"), new PathPerson(b, "Adult B"))));
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
}
