package com.familyconnect.modules.community.api;

import java.util.Optional;
import java.util.UUID;

/** Dữ liệu của module Community cho module khác đọc (heritage-directory-dashboard.md: CommunityQuery.getHeritageMedia). */
public interface CommunityQuery {

    /** Media còn hoạt động; module khác dùng để kiểm tra ownerType, ownerId, familyId, không join bảng. */
    Optional<MediaInfo> getMedia(UUID mediaId);

    record MediaInfo(UUID id, String ownerType, UUID ownerId, UUID familyId, String contentUrl) {
    }

    /** Tệp tải lên; rỗng nếu không tồn tại. Module gọi tự kiểm tra ownerType và familyId. */
    Optional<HeritageMedia> getHeritageMedia(UUID mediaId);

    record HeritageMedia(UUID id, UUID familyId, String ownerType, String contentUrl) {
    }

    /** Ảnh đại diện (owner_type = PERSON); rỗng nếu không tồn tại hoặc không phải ảnh của hồ sơ thành viên. */
    Optional<PersonMedia> getPersonMedia(UUID mediaId);

    record PersonMedia(UUID id, UUID personId) {
    }
}
