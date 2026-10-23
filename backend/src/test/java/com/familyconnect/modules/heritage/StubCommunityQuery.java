package com.familyconnect.modules.heritage;

import com.familyconnect.modules.heritage.api.CommunityQuery;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StubCommunityQuery implements CommunityQuery {

    private final Map<UUID, HeritageMedia> media = new HashMap<>();

    public void add(UUID mediaId, UUID familyId, String ownerType) {
        media.put(mediaId, new HeritageMedia(mediaId, familyId, ownerType,
                "/api/v1/media/" + mediaId + "/content"));
    }

    @Override
    public Optional<HeritageMedia> findHeritageMedia(UUID mediaId) {
        return Optional.ofNullable(media.get(mediaId));
    }
}
