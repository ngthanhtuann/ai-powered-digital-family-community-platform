package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.api.SummarySource;
import com.familyconnect.modules.ai.api.SummarySource.SummaryText;
import com.familyconnect.modules.ai.dto.SummaryRequest;
import com.familyconnect.modules.ai.dto.SummaryResponse;
import com.familyconnect.modules.ai.repository.AiSummaryCache;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** UC-AI-04: tóm tắt bài đăng, câu chuyện, tài liệu; nội dung lấy qua {@link SummarySource} của module sở hữu. */
@Service
public class AiSummaryService {
    static final String NOT_FOUND = "Không tìm thấy thông tin.";
    private static final int PART_CHARS = 8000;

    private final Map<String, SummarySource> sources = new HashMap<>();
    private final AiSummaryCache cache;
    private final ContentHashService hashes;
    private final SummaryLlm llm;
    private final SummaryRateLimiter limiter;
    private final boolean enabled;
    private final SystemConfigQuery config;
    private final int maxInput;

    public AiSummaryService(List<SummarySource> sources, AiSummaryCache cache,
                            ContentHashService hashes, SummaryLlm llm, SummaryRateLimiter limiter,
                            SystemConfigQuery config,
                            @Value("${ai.summary.enabled:true}") boolean enabled,
                            @Value("${ai.summary.max_input_chars:30000}") int maxInput) {
        sources.forEach(source -> source.sourceTypes().forEach(type -> this.sources.put(type, source)));
        this.cache = cache; this.hashes = hashes; this.config = config;
        this.llm = llm; this.limiter = limiter; this.enabled = enabled; this.maxInput = maxInput;
    }

    public SummaryResponse summarize(AuthenticatedUser user, SummaryRequest request) {
        // Biến môi trường là công tắc lúc triển khai; cấu hình hệ thống là công tắc của quản trị (SCRUM-94).
        if (!enabled || !config.getBoolean(SystemConfigQuery.AI_SUMMARY_ENABLED)) throw new ApiException(ErrorCode.AI_002);
        SummarySource owner = sources.get(request.sourceType());
        if (owner == null) throw new ApiException(ErrorCode.VALID_001);
        // Module sở hữu kiểm quyền đọc (403/404) trước khi AI nhận văn bản (BR-AI-02).
        SummaryText source = owner.readForSummary(request.sourceType(), request.sourceId());
        String text = source.text() == null ? "" : source.text().trim();
        if (text.isEmpty()) return new SummaryResponse(NOT_FOUND, request.sourceType(), request.sourceId(), false);
        if (text.length() > maxInput) throw ApiException.invalidField("sourceId", "Nội dung quá dài để tóm tắt.");
        String hash = hashes.hash(text);
        var cached = cache.find(source.familyId(), request.sourceType(), request.sourceId(), hash);
        if (cached.isPresent()) return new SummaryResponse(cached.get(), request.sourceType(), request.sourceId(), true);
        // Chỉ lần gọi LLM mới tính hạn mức; đọc cache không tốn lượt.
        limiter.check(user.id());
        String summary;
        try {
            summary = text.length() <= PART_CHARS ? llm.summarize(text) : summarizeInParts(text);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.AI_001);
        }
        cache.save(source.familyId(), request.sourceType(), request.sourceId(), hash, summary, llm.modelName(), user.id());
        return new SummaryResponse(summary, request.sourceType(), request.sourceId(), false);
    }

    private String summarizeInParts(String text) {
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < text.length(); i += PART_CHARS) {
            if (!combined.isEmpty()) combined.append("\n");
            combined.append(llm.summarize(text.substring(i, Math.min(i + PART_CHARS, text.length()))));
        }
        return llm.summarize(combined.toString());
    }
}
