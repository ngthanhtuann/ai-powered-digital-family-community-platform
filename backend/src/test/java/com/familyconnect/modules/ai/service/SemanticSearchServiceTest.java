package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.SemanticSearchResponse;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

    private EmbeddingPipelineService embedding;
    private EmbeddingChunkRepository repository;
    private MembershipQuery membership;
    private AiSearchRateLimiter rateLimiter;
    private GenealogyQuery genealogyQuery;
    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        embedding = mock(EmbeddingPipelineService.class);
        repository = mock(EmbeddingChunkRepository.class);
        membership = mock(MembershipQuery.class);
        rateLimiter = mock(AiSearchRateLimiter.class);
        genealogyQuery = mock(GenealogyQuery.class);
        service = new SemanticSearchService(embedding, repository, membership, rateLimiter, genealogyQuery,
                CLOCK, 0.88, true);
    }

    @Test
    void searchScopesRetrievalToCallersFamilyAndHidesMinorsForMember() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID minor = UUID.randomUUID();
        when(membership.getMembership(userId)).thenReturn(new MembershipView(userId, familyId, null, EffectiveRole.MEMBER, true));
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.createEmbedding("người làm bác sĩ ở Hà Nội")).thenReturn(new float[768]);
        when(genealogyQuery.findMinorPersonIds(familyId, LocalDate.now(CLOCK))).thenReturn(List.of(minor));

        SemanticSearchResponse response = service.search(new AuthenticatedUser(userId, List.of("MEMBER")),
                "người làm bác sĩ ở Hà Nội", null, 0, 10);

        assertTrue(response.results().isEmpty());
        verify(membership).requireVerified(userId);
        verify(membership).requireFamilyAccess(userId, familyId);
        // Thành viên chưa thuộc chi nào: chỉ nội dung toàn gia đình, loại trẻ dưới 16 tuổi.
        verify(repository).search(eq(familyId), eq(false), eq(List.of()), any(float[].class), eq(0.88),
                eq(List.of(minor)), eq(0), eq(10));
        verify(repository, never()).findBranchIds(any());
    }

    @Test
    void memberSeesOwnAndAncestorBranchesOnly() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID own = UUID.randomUUID();
        UUID ancestor = UUID.randomUUID();
        UUID sibling = UUID.randomUUID();
        when(membership.getMembership(userId)).thenReturn(new MembershipView(userId, familyId, own, EffectiveRole.MEMBER, true));
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.createEmbedding(anyString())).thenReturn(new float[768]);
        when(repository.findBranchIds(familyId)).thenReturn(List.of(own, ancestor, sibling));
        when(genealogyQuery.isBranchWithin(familyId, own, own)).thenReturn(true);
        when(genealogyQuery.isBranchWithin(familyId, own, ancestor)).thenReturn(true);

        service.search(new AuthenticatedUser(userId, List.of("MEMBER")), "họp chi", null, 0, 10);

        verify(repository).search(eq(familyId), eq(false), eq(List.of(own, ancestor)), any(float[].class),
                eq(0.88), anyList(), eq(0), eq(10));
    }

    @Test
    void wholeFamilyBranchAdminSeesAllBranchesAndMinors() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(membership.getMembership(userId)).thenReturn(
                new MembershipView(userId, familyId, null, EffectiveRole.BRANCH_ADMIN, true));
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.createEmbedding(anyString())).thenReturn(new float[768]);

        service.search(new AuthenticatedUser(userId, List.of("MEMBER")), "giỗ tổ", null, 0, 10);

        verify(repository).search(eq(familyId), eq(true), eq(List.of()), any(float[].class), eq(0.88),
                eq(List.of()), eq(0), eq(10));
        verify(genealogyQuery, never()).findMinorPersonIds(any(), any());
    }

    @Test
    void rejectsFamilyOverrideOutsideMembership() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID otherFamily = UUID.randomUUID();
        when(membership.getMembership(userId)).thenReturn(new MembershipView(userId, familyId, null, EffectiveRole.MEMBER, true));
        ApiException error = assertThrows(ApiException.class, () -> service.search(
                new AuthenticatedUser(userId, List.of("MEMBER")), "query", otherFamily, 0, 10));
        assertEquals("PERM_001", error.code().name());
        verifyNoInteractions(repository, embedding, genealogyQuery);
    }

    @Test
    void rejectsInvalidPageSize() {
        UUID userId = UUID.randomUUID();
        ApiException error = assertThrows(ApiException.class, () -> service.search(
                new AuthenticatedUser(userId, List.of("MEMBER")), "query", null, 0, 101));
        assertEquals("VALID_001", error.code().name());
        verifyNoInteractions(membership, repository, embedding, genealogyQuery);
    }
}
