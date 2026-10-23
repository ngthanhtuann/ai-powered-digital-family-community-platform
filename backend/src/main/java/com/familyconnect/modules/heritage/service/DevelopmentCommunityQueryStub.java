package com.familyconnect.modules.heritage.service;

import com.familyconnect.media.MediaUploadService;
import com.familyconnect.modules.heritage.api.CommunityQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 3 adapter for the Community upload spike. For HERITAGE, the spike's ownerId is
 * supplied as familyId; replace this mapping with SCRUM-46 media metadata.
 */
@Component
@Profile("development")
public class DevelopmentCommunityQueryStub implements CommunityQuery {

    private final MediaUploadService mediaUploadService;

    public DevelopmentCommunityQueryStub(MediaUploadService mediaUploadService) {
        this.mediaUploadService = mediaUploadService;
    }

    @Override
    public Optional<HeritageMedia> findHeritageMedia(UUID mediaId) {
        return mediaUploadService.findById(mediaId)
                .map(media -> new HeritageMedia(
                        media.id(),
                        media.ownerId(),
                        media.ownerType(),
                        "/api/v1/media/" + media.id() + "/content"
                ));
    }
}
