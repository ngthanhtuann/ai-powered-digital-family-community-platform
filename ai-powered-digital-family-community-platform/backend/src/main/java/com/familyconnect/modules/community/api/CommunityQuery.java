package com.familyconnect.modules.community.api;

import java.util.Optional;
import java.util.UUID;

/** Dữ liệu của module Community cho module khác đọc (heritage-directory-dashboard.md: CommunityQuery.getHeritageMedia). */
public interface CommunityQuery {

    /** Tệp tải lên; rỗng nếu không tồn tại. Module gọi tự kiểm tra ownerType và familyId. */
    Optional<HeritageMedia> getHeritageMedia(UUID mediaId);

    record HeritageMedia(UUID id, UUID familyId, String ownerType, String contentUrl) {
    }
}
