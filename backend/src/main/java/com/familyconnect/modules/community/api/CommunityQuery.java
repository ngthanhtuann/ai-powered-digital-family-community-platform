package com.familyconnect.modules.community.api;

import java.util.Optional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Dữ liệu của module Community cho module khác đọc (heritage-directory-dashboard.md: CommunityQuery.getHeritageMedia). */
public interface CommunityQuery {

    /** Tệp tải lên; rỗng nếu không tồn tại. Module gọi tự kiểm tra ownerType và familyId. */
    Optional<HeritageMedia> getHeritageMedia(UUID mediaId);

    record HeritageMedia(UUID id, UUID familyId, String ownerType, String contentUrl) {
    }

    /** Ảnh đại diện (owner_type = PERSON); rỗng nếu không tồn tại hoặc không phải ảnh của hồ sơ thành viên. */
    Optional<PersonMedia> getPersonMedia(UUID mediaId);

    record PersonMedia(UUID id, UUID personId) {
    }

    List<WeeklyPostCount> getWeeklyPostCounts(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                               OffsetDateTime fromInclusive, OffsetDateTime toExclusive);

    record WeeklyPostCount(LocalDate weekStart, long postCount) {
    }
}
