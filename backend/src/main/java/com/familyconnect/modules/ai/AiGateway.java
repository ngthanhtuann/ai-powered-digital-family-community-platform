package com.familyconnect.modules.ai;

import java.util.UUID;

public interface AiGateway {

    void indexPerson(UUID personId);

    void indexPost(UUID postId);

    void removeSource(String sourceType, UUID sourceId);
}