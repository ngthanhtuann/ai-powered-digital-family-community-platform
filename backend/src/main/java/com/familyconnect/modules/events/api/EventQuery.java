package com.familyconnect.modules.events.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only event data exposed to other modules. */
public interface EventQuery {

    List<UpcomingEvent> getUpcomingEvents(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                          OffsetDateTime fromInclusive, int limit);

    record UpcomingEvent(UUID id, String title, OffsetDateTime startAt) {
    }
}
