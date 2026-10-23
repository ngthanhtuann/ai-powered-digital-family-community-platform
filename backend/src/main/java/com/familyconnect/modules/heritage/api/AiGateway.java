package com.familyconnect.modules.heritage.api;

import java.util.UUID;

/** Outbound notification to AI when searchable Heritage content changes. */
public interface AiGateway {

    void refreshEmbedding(ContentType contentType, UUID contentId, UUID familyId);

    enum ContentType {
        DOCUMENT,
        STORY
    }
}
