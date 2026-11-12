package com.familyconnect.modules.events.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only event data exposed to other modules. */
public interface EventQuery {

    List<UpcomingEvent> getUpcomingEvents(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                          OffsetDateTime fromInclusive, int limit);

    List<ReportEvent> getReportEvents(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                      OffsetDateTime fromInclusive, OffsetDateTime toExclusive);

    record UpcomingEvent(UUID id, String title, OffsetDateTime startAt) {
    }

    record ReportEvent(UUID id, String title, OffsetDateTime startAt,
                       long going, long notGoing, long maybe) {
    }
}
