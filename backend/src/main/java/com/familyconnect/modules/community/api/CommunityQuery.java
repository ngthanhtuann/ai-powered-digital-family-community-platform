package com.familyconnect.modules.community.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Dữ liệu của module Community cho module khác đọc (heritage-directory-dashboard.md: CommunityQuery.getHeritageMedia). */
public interface CommunityQuery {

    /** Tệp tải lên; rỗng nếu không tồn tại. Module gọi tự kiểm tra ownerType và familyId. */
    Optional<HeritageMedia> getHeritageMedia(UUID mediaId);

    record HeritageMedia(UUID id, UUID familyId, String ownerType, String fileType,
                         String caption, String contentUrl) {
    }

    HeritagePhotoPage getHeritagePhotos(UUID familyId, UUID mediaId, List<UUID> excludedMediaIds,
                                        int page, int size, boolean ascending);

    record HeritagePhoto(UUID id, String contentUrl, String caption,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    record HeritagePhotoPage(List<HeritagePhoto> items, long total) {
        public HeritagePhotoPage {
            items = List.copyOf(items);
        }
    }

    /** Ảnh đại diện (owner_type = PERSON); rỗng nếu không tồn tại hoặc không phải ảnh của hồ sơ thành viên. */
    Optional<PersonMedia> getPersonMedia(UUID mediaId);

    record PersonMedia(UUID id, UUID personId) {
    }

    List<WeeklyPostCount> getWeeklyPostCounts(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                               OffsetDateTime fromInclusive, OffsetDateTime toExclusive);

    record WeeklyPostCount(LocalDate weekStart, long postCount) {
    }

    List<WeeklyActivityCount> getWeeklyActivityCounts(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                                       OffsetDateTime fromInclusive, OffsetDateTime toExclusive);

    record WeeklyActivityCount(LocalDate weekStart, long postCount, long commentCount, long reactionCount) {
    }
}
