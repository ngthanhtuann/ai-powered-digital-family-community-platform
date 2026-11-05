package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.dto.SummaryRequest;
import com.familyconnect.modules.ai.dto.SummaryResponse;
import com.familyconnect.modules.ai.repository.AiSummaryCache;
import com.familyconnect.modules.community.service.CommunityService;
import com.familyconnect.modules.heritage.service.HeritageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AiSummaryService {
    private final CommunityService community;
    private final HeritageService heritage;
    private final AiSummaryCache cache;
    private final ContentHashService hashes;
    private final SummaryLlm llm;
    private final SummaryRateLimiter limiter;
    private final boolean enabled;
    private final int maxInput;
    public AiSummaryService(CommunityService community, HeritageService heritage, AiSummaryCache cache,
                            ContentHashService hashes, SummaryLlm llm, SummaryRateLimiter limiter,
                            @Value("${ai.summary.enabled:true}") boolean enabled,
                            @Value("${ai.summary.max_input_chars:30000}") int maxInput) {
        this.community=community; this.heritage=heritage; this.cache=cache; this.hashes=hashes;
        this.llm=llm; this.limiter=limiter; this.enabled=enabled; this.maxInput=maxInput;
    }
    public SummaryResponse summarize(AuthenticatedUser user, SummaryRequest request) {
        if (!enabled) throw new ApiException(ErrorCode.AI_002);
        String source = switch (request.sourceType()) {
            case "POST" -> community.summarySource(request.sourceId());
            case "STORY", "DOCUMENT" -> heritage.summarySource(request.sourceType(), request.sourceId());
            default -> throw new ApiException(ErrorCode.VALID_001);
        };
        if (source.isBlank() || source.length() > maxInput) throw new ApiException(ErrorCode.VALID_001);
        String hash = hashes.hash(source);
        UUID familyId = switch (request.sourceType()) {
            case "STORY", "DOCUMENT" -> heritageFamily(request);
            case "POST" -> community.summaryFamilyId(request.sourceId());
            default -> throw new ApiException(ErrorCode.VALID_001);
        };
        limiter.check(user.id());
        var cached = cache.find(hash);
        if (cached.isPresent()) return new SummaryResponse(cached.get(), request.sourceType(), request.sourceId(), true);
        try {
            String summary;
            if (source.length() <= 8000) summary = llm.summarize(source);
            else {
                StringBuilder combined = new StringBuilder();
                for (int i=0; i<source.length(); i+=8000) {
                    if (!combined.isEmpty()) combined.append("\n");
                    combined.append(llm.summarize(source.substring(i, Math.min(i+8000, source.length()))));
                }
                summary = llm.summarize(combined.toString());
            }
            cache.save(hash, familyId, request.sourceType(), request.sourceId(), summary);
            return new SummaryResponse(summary, request.sourceType(), request.sourceId(), false);
        } catch (RuntimeException ex) { throw new ApiException(ErrorCode.AI_001); }
    }
    private UUID heritageFamily(SummaryRequest request) {
        return heritage.familyIdForSummary(request.sourceType(), request.sourceId());
    }
}
