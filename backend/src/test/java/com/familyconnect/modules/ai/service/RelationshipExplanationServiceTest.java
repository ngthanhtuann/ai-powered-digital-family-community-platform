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

    /** SCRUM-141: họ hàng xa 4 đời mỗi bên; prompt có đủ từng bước, tổ tiên chung, chênh lệch đời và cách gọi hai chiều. */
    @Test void distantRelativePromptWalksEveryStepAndGivesBothForms() {
        GenealogyQuery genealogy = mock(GenealogyQuery.class);
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        UUID family = UUID.randomUUID();
        // A -> cha -> ông -> cụ -> Tổ chung <- con trưởng <- cháu <- chắt <- B (B ở đời dưới A 1 đời)
        String[] names = {"An", "Bình", "Cường", "Dũng", "Tổ chung", "Hải", "Khoa", "Long", "Minh", "Bảo"};
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) ids.add(UUID.randomUUID());
        List<PathStep> path = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) path.add(new PathStep(ids.get(i), names[i]));
        UUID a = ids.get(0), b = ids.get(names.length - 1);
        when(genealogy.findPersonFamilyId(any())).thenReturn(java.util.Optional.of(family));
        when(genealogy.findMinorPersonIds(any(), any())).thenReturn(List.of());
        when(genealogy.relationship(a, b)).thenReturn(new RelationshipResult(true, "bác/chú họ (bên nội)", 4, 5, path));
        when(genealogy.relationship(b, a)).thenReturn(new RelationshipResult(true, "cháu họ (bên nội)", 5, 4, List.of()));
        for (int i = 0; i + 1 < names.length; i++) {
            String label = i < 4 ? "con" : "cha";
            when(genealogy.relationship(ids.get(i), ids.get(i + 1))).thenReturn(new RelationshipResult(true, label, null, null, List.of()));
        }
        when(model.isConfigured()).thenReturn(true);
        when(model.answer(anyString())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            assertTrue(prompt.contains("An là bác/chú họ (bên nội) của Bảo"));
            assertTrue(prompt.contains("Bảo là cháu họ (bên nội) của An"));
            assertTrue(prompt.contains("Bảo gọi An là \"bác/chú họ (bên nội)\""));
            assertTrue(prompt.contains("Tổ tiên chung gần nhất: Tổ chung"));
            assertTrue(prompt.contains("An ở đời trên 1 đời"));
            assertTrue(prompt.contains("1. An là con của Bình."));
            assertTrue(prompt.contains("9. Minh là cha của Bảo."));
            assertTrue(prompt.contains("tóm thành hai nhánh"));
            assertFalse(prompt.contains("ngắn gọn"));
            assertTrue(prompt.contains("không thêm danh xưng"));
            return "An là bác họ của Bảo vì hai người cùng tổ tiên chung là Tổ chung.";
        });
        var service = new RelationshipExplanationService(genealogy, mock(MembershipQuery.class), model,
                mock(AiSearchRateLimiter.class), Clock.systemUTC(), true);
        var response = service.explain(new AuthenticatedUser(UUID.randomUUID(), List.of("MEMBER")),
                new RelationshipExplanationRequest(a, b));
        assertTrue(response.found());
        assertTrue(response.explanation().contains("tổ tiên chung"));
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
