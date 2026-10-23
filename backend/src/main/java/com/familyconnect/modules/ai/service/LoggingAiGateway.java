package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.api.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Bản tạm đến khi SCRUM-41 làm pipeline embedding: chỉ ghi log, không ghi nội dung (privacy.md). */
@Component
public class LoggingAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingAiGateway.class);

    @Override
    public void upsertSource(SourceType type, UUID sourceId, UUID familyId, UUID branchId, String content) {
        log.info("AI upsertSource: type={}, sourceId={}, familyId={}", type, sourceId, familyId);
    }

    @Override
    public void removeSource(SourceType type, UUID sourceId) {
        log.info("AI removeSource: type={}, sourceId={}", type, sourceId);
    }
}
