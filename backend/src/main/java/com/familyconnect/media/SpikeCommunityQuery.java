package com.familyconnect.media;

import com.familyconnect.modules.community.api.CommunityQuery;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Bản tạm dựa trên spike upload (SCRUM-31): ownerId của tệp HERITAGE được hiểu là familyId.
 * SCRUM-46 thay bằng bảng media thật của module Community, kiểm tra family_id ở server.
 */
@Component
public class SpikeCommunityQuery implements CommunityQuery {

    private final MediaUploadService mediaUploadService;

    public SpikeCommunityQuery(MediaUploadService mediaUploadService) {
        this.mediaUploadService = mediaUploadService;
    }

    @Override
    public Optional<HeritageMedia> getHeritageMedia(UUID mediaId) {
        return mediaUploadService.findById(mediaId)
                .map(media -> new HeritageMedia(media.id(), media.ownerId(), media.ownerType(),
                        "/api/v1/media/" + media.id() + "/content"));
    }

    @Override
    public Optional<PersonMedia> getPersonMedia(UUID mediaId) {
        return mediaUploadService.findById(mediaId)
                .filter(media -> "PERSON".equals(media.ownerType()))
                .map(media -> new PersonMedia(media.id(), media.ownerId()));
    }
}
