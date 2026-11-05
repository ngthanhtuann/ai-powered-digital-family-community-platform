package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.api.SummarySource;
import com.familyconnect.modules.ai.api.SummarySource.SummaryText;
import com.familyconnect.modules.ai.dto.SummaryRequest;
import com.familyconnect.modules.ai.repository.AiSummaryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiSummaryServiceTest {
    private final UUID family = UUID.randomUUID();
    private final AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), List.of("MEMBER"));
    private SummarySource stories;
    private AiSummaryCache cache;
    private SummaryLlm llm;
    private AiSummaryService service;

    @BeforeEach
    void setUp() {
        stories = mock(SummarySource.class);
        when(stories.sourceTypes()).thenReturn(Set.of("STORY", "DOCUMENT"));
        cache = mock(AiSummaryCache.class);
        llm = mock(SummaryLlm.class);
        when(llm.modelName()).thenReturn("model-gia");
        when(cache.find(any(), anyString(), any(), anyString())).thenReturn(Optional.empty());
        service = new AiSummaryService(List.of(stories), cache, new ContentHashService(), llm,
                new SummaryRateLimiter(20, Clock.systemUTC()), true, 30000);
    }

    @Test
    void summarizesTenSyntheticSourcesAndReturnsOnlyTheModelOutput() {
        ContentHashService hashes = new ContentHashService();
        for (int i = 1; i <= 10; i++) {
            UUID id = UUID.randomUUID();
            String source = "Dữ liệu giả số " + i + ": Gia đình tổ chức buổi họp mặt vào tháng " + i + ".";
            String expected = "Gia đình tổ chức buổi họp mặt vào tháng " + i + ".";
            when(stories.readForSummary("STORY", id)).thenReturn(new SummaryText(family, source));
            when(llm.summarize(source)).thenReturn(expected);
            var result = service.summarize(actor, new SummaryRequest("STORY", id));
            assertEquals(expected, result.summary());
            assertFalse(result.cached());
            verify(cache).save(family, "STORY", id, hashes.hash(source), expected, "model-gia", actor.id());
        }
        verify(llm, times(10)).summarize(anyString());
    }

    @Test
    void emptySourceReturnsNotFoundWithoutCallingModel() {
        UUID id = UUID.randomUUID();
        when(stories.readForSummary("DOCUMENT", id)).thenReturn(new SummaryText(family, "  "));
        assertEquals("Không tìm thấy thông tin.", service.summarize(actor, new SummaryRequest("DOCUMENT", id)).summary());
        verifyNoInteractions(llm);
    }

    @Test
    void unknownSourceTypeIsRejected() {
        assertEquals(ErrorCode.VALID_001, assertThrows(ApiException.class,
                () -> service.summarize(actor, new SummaryRequest("POST", UUID.randomUUID()))).code());
    }
}
