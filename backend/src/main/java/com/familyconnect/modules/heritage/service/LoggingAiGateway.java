package com.familyconnect.modules.heritage.service;

import com.familyconnect.modules.heritage.api.AiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Sprint 3 adapter until the AI module provides its SCRUM-41 implementation. */
@Component
public class LoggingAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingAiGateway.class);

    @Override
    public void refreshEmbedding(ContentType contentType, UUID contentId, UUID familyId) {
        log.info("AI embedding refresh requested: type={}, contentId={}, familyId={}",
                contentType, contentId, familyId);
    }
}
