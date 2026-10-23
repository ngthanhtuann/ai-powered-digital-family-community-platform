package com.familyconnect.modules.heritage.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only access to Community media needed by Heritage. */
public interface CommunityQuery {

    Optional<HeritageMedia> findHeritageMedia(UUID mediaId);

    record HeritageMedia(UUID id, UUID familyId, String ownerType, String contentUrl) {
    }
}
