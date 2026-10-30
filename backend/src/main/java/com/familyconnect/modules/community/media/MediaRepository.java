package com.familyconnect.modules.community.media;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Metadata media của Community; các module khác chỉ dùng CommunityQuery. */
public interface MediaRepository {
    record Media(UUID id, String ownerType, UUID ownerId, UUID familyId, long fileSize,
                 String fileName, String storageKey, String caption, String fileType,
                 UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    record Page(List<Media> items, long total) {}

    Media insert(UUID id, String ownerType, UUID ownerId, UUID familyId,
                 long size, String fileName, String storageKey, String caption,
                 String mimeType, UUID createdBy);

    Optional<Media> find(UUID id);

    Page list(String ownerType, UUID ownerId, int page, int size, String order);

    /** Số media đang hoạt động theo từng chủ sở hữu, một truy vấn cho cả trang. */
    java.util.Map<UUID, Long> countByOwners(String ownerType, List<UUID> ownerIds);

    /** Tối đa {@code perOwner} media mới nhất của từng chủ sở hữu, một truy vấn cho cả trang. */
    java.util.Map<UUID, List<Media>> latestByOwners(String ownerType, List<UUID> ownerIds, int perOwner);

    boolean softDelete(UUID id, UUID actorId);
}
