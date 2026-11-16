package com.familyconnect.modules.heritage;

import com.familyconnect.modules.community.api.CommunityQuery;
import com.familyconnect.modules.community.api.CommunityQuery.HeritagePhoto;
import com.familyconnect.modules.community.api.CommunityQuery.HeritagePhotoPage;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class StubCommunityQuery implements CommunityQuery {

    private final Map<UUID, HeritageMedia> media = new HashMap<>();

    public void add(UUID mediaId, UUID familyId, String ownerType) {
        media.put(mediaId, new HeritageMedia(mediaId, familyId, ownerType,
                "image/jpeg", null, "/api/v1/media/" + mediaId + "/content"));
    }

    @Override
    public Optional<HeritageMedia> getHeritageMedia(UUID mediaId) {
        return Optional.ofNullable(media.get(mediaId));
    }

    @Override
    public Optional<PersonMedia> getPersonMedia(UUID mediaId) {
        return Optional.empty();
    }

    @Override
    public HeritagePhotoPage getHeritagePhotos(UUID familyId, UUID mediaId, List<UUID> excludedMediaIds,
                                                int page, int size, boolean ascending) {
        return new HeritagePhotoPage(List.of(), 0);
    }

    @Override
    public List<WeeklyPostCount> getWeeklyPostCounts(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                                     OffsetDateTime fromInclusive, OffsetDateTime toExclusive) {
        return List.of();
    }

    @Override
    public List<WeeklyActivityCount> getWeeklyActivityCounts(UUID familyId, boolean allBranches,
                                                              List<UUID> branchIds,
                                                              OffsetDateTime fromInclusive,
                                                              OffsetDateTime toExclusive) {
        return List.of();
    }
}
