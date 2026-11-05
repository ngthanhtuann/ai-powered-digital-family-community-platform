package com.familyconnect.modules.ai.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.ChatRequest;
import com.familyconnect.modules.ai.repository.ChatRepository;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiChatGroundingTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private EmbeddingPipelineService embeddings;
    private ChatLanguageModel model;
    private EmbeddingChunkRepository chunks;
    private ChatRepository chats;
    private MembershipQuery membership;
    private GenealogyQuery genealogy;
    private ChatService service;

    @BeforeEach void setUp() {
        embeddings = mock(EmbeddingPipelineService.class); model = mock(ChatLanguageModel.class);
        chunks = mock(EmbeddingChunkRepository.class); chats = mock(ChatRepository.class);
        membership = mock(MembershipQuery.class); genealogy = mock(GenealogyQuery.class);
        AiChatRateLimiter limiter = mock(AiChatRateLimiter.class);
        AuditLogger audit = mock(AuditLogger.class);
        when(membership.getMembership(userId)).thenReturn(new MembershipView(userId, familyId, null, EffectiveRole.MEMBER, true));
        when(embeddings.isConfigured()).thenReturn(true); when(embeddings.createEmbedding(anyString())).thenReturn(new float[768]);
        when(model.isConfigured()).thenReturn(true); when(model.answer(anyString())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            assertTrue(prompt.contains("Chỉ trả lời dựa trên NGỮ CẢNH"));
            assertTrue(prompt.contains("Hồ sơ giả: Nguyễn An sinh năm 1950."));
            return "Theo hồ sơ giả, Nguyễn An sinh năm 1950.";
        });
        when(chunks.searchChat(eq(familyId), eq(false), anyList(), any(), anyList(), anyInt()))
                .thenReturn(List.of(new EmbeddingChunkRepository.ChatChunk("PERSON", UUID.randomUUID(),
                        "Hồ sơ giả: Nguyễn An sinh năm 1950.", 0.9)));
        when(chats.createSession(eq(userId), eq(familyId))).thenAnswer(call -> UUID.randomUUID());
        when(chats.recentMessages(any(), anyInt())).thenReturn(List.of());
        when(chats.saveExchange(any(), any(), anyString(), anyString(), anyList(), any(), anyLong())).thenReturn(UUID.randomUUID());
        service = new ChatService(embeddings, model, chunks, chats, membership, genealogy, limiter, audit,
                Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC), 5, .55, 30, true);
    }

    @Test void answersTwentySyntheticQuestionsFromRetrievedContext() {
        AuthenticatedUser user = new AuthenticatedUser(userId, List.of("MEMBER"));
        for (int i = 1; i <= 20; i++) {
            var response = service.chat(user, new ChatRequest("Câu hỏi giả số " + i + "?", null, null));
            assertEquals("Theo hồ sơ giả, Nguyễn An sinh năm 1950.", response.answer());
            assertEquals(1, response.sources().size());
        }
        verify(model, times(20)).answer(anyString());
    }

    @Test void returnsNotFoundWithoutCallingModelWhenNothingIsRetrieved() {
        when(chunks.searchChat(any(), anyBoolean(), anyList(), any(), anyList(), anyInt())).thenReturn(List.of());
        var response = service.chat(new AuthenticatedUser(userId, List.of("MEMBER")),
                new ChatRequest("Thông tin không có trong dữ liệu?", null, null));
        assertEquals("Không tìm thấy thông tin.", response.answer());
        assertTrue(response.sources().isEmpty());
        verify(model, never()).answer(anyString());
    }

    @Test void rejectsUnverifiedAccountBeforeRateLimit() {
        doThrow(new ApiException(ErrorCode.PERM_002)).when(membership).requireVerified(userId);
        assertEquals(ErrorCode.PERM_002, assertThrows(ApiException.class, () -> service.chat(
                new AuthenticatedUser(userId, List.of("MEMBER")), new ChatRequest("Câu hỏi", null, null))).code());
        verifyNoInteractions(model);
    }

    @Test void foreignSessionIsRejectedBeforeRateLimit() {
        AiChatRateLimiter limiter = mock(AiChatRateLimiter.class);
        service = new ChatService(embeddings, model, chunks, chats, membership, genealogy, limiter, mock(AuditLogger.class),
                Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC), 5, .55, 30, true);
        UUID foreign = UUID.randomUUID();
        when(chats.ownsActiveSession(foreign, userId, familyId)).thenReturn(false);
        assertEquals(ErrorCode.PERM_001, assertThrows(ApiException.class, () -> service.chat(
                new AuthenticatedUser(userId, List.of("MEMBER")), new ChatRequest("Câu hỏi", foreign, null))).code());
        verifyNoInteractions(limiter);
    }

    @Test void rateLimitReturnsRate001() {
        AiChatRateLimiter limiter = new AiChatRateLimiter(1,
                Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC));
        limiter.check(userId);
        assertEquals(ErrorCode.RATE_001, assertThrows(ApiException.class, () -> limiter.check(userId)).code());
    }
}
