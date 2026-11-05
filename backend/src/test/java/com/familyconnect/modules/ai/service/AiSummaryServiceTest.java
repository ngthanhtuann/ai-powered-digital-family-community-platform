package com.familyconnect.modules.ai.service;

import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.SummaryRequest;
import com.familyconnect.modules.ai.repository.AiSummaryCache;
import com.familyconnect.modules.community.service.CommunityService;
import com.familyconnect.modules.heritage.service.HeritageService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiSummaryServiceTest {
    @Test
    void summarizesTenSyntheticSourcesAndReturnsOnlyTheModelOutput() {
        CommunityService community = mock(CommunityService.class);
        HeritageService heritage = mock(HeritageService.class);
        AiSummaryCache cache = mock(AiSummaryCache.class);
        ContentHashService hashes = new ContentHashService();
        SummaryLlm llm = mock(SummaryLlm.class);
        SummaryRateLimiter limiter = new SummaryRateLimiter(20, Clock.systemUTC());
        AiSummaryService service = new AiSummaryService(community, heritage, cache, hashes, llm,
                limiter, true, 30000);
        UUID family = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(cache.find(anyString())).thenReturn(Optional.empty());
        for (int i = 1; i <= 10; i++) {
            UUID id = UUID.randomUUID();
            String source = "Dữ liệu giả số " + i + ": Gia đình tổ chức buổi họp mặt vào tháng " + i + ".";
            String expected = "Gia đình tổ chức buổi họp mặt vào tháng " + i + ".";
            when(heritage.summarySource("STORY", id)).thenReturn(source);
            when(heritage.familyIdForSummary("STORY", id)).thenReturn(family);
            when(llm.summarize(source)).thenReturn(expected);
            var result = service.summarize(new AuthenticatedUser(actor, List.of("MEMBER")),
                    new SummaryRequest("STORY", id));
            assertEquals(expected, result.summary());
            assertEquals(false, result.cached());
            verify(cache).save(hashes.hash(source), family, "STORY", id, expected);
        }
        verify(llm, times(10)).summarize(anyString());
        verifyNoInteractions(community);
    }
}
