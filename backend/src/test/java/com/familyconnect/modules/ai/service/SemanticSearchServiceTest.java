package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.modules.ai.dto.SemanticSearchResponse;
import com.familyconnect.modules.ai.api.PersonVisibilityQuery;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.common.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchServiceTest {
    private EmbeddingPipelineService embedding;
    private EmbeddingChunkRepository repository;
    private MembershipQuery membership;
    private AiSearchRateLimiter rateLimiter;
    private PersonVisibilityQuery personVisibilityQuery;
    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        embedding = mock(EmbeddingPipelineService.class);
        repository = mock(EmbeddingChunkRepository.class);
        membership = mock(MembershipQuery.class);
        rateLimiter = mock(AiSearchRateLimiter.class);
        personVisibilityQuery = mock(PersonVisibilityQuery.class);
        service = new SemanticSearchService(embedding, repository, membership, rateLimiter, personVisibilityQuery,
                Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC), 0.88, true);
    }

    @Test
    void searchScopesRetrievalToCallersFamilyAndHidesMinorsForMember() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        when(membership.getMembership(userId)).thenReturn(new MembershipView(userId, familyId, null, EffectiveRole.MEMBER, true));
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.createEmbedding("người làm bác sĩ ở Hà Nội")).thenReturn(new float[768]);
        when(repository.search(eq(familyId), isNull(), any(float[].class), eq(0.88), anyList(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(repository.countSearch(eq(familyId), isNull(), anyList(), any(float[].class), eq(0.88))).thenReturn(0L);

        SemanticSearchResponse response = service.search(new AuthenticatedUser(userId, List.of("MEMBER")),
                "người làm bác sĩ ở Hà Nội", null, 0, 10);

        assertTrue(response.results().isEmpty());
        assertEquals(0, response.total());
        verify(membership).requireVerified(userId);
        verify(membership).requireFamilyAccess(userId, familyId);
        verify(repository).search(eq(familyId), isNull(), any(float[].class), eq(0.88), anyList(), eq(0), eq(10));
        verify(personVisibilityQuery).findMinorPersonIds(eq(familyId), any());
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
        verifyNoInteractions(repository, embedding, personVisibilityQuery);
    }

    @Test
    void rejectsInvalidPageSize() {
        UUID userId = UUID.randomUUID();
        ApiException error = assertThrows(ApiException.class, () -> service.search(
                new AuthenticatedUser(userId, List.of("MEMBER")), "query", null, 0, 101));
        assertEquals("VALID_001", error.code().name());
        verifyNoInteractions(membership, repository, embedding, personVisibilityQuery);
    }
}
