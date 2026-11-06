package com.familyconnect.modules.events.service;

import com.familyconnect.modules.events.api.EventQuery;
import com.familyconnect.modules.events.repository.EventStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventQueryImpl implements EventQuery {

    private final EventStore eventStore;

    public EventQueryImpl(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public List<UpcomingEvent> getUpcomingEvents(UUID familyId, boolean allBranches, List<UUID> branchIds,
                                                 OffsetDateTime fromInclusive, int limit) {
        return eventStore.upcomingEvents(familyId, allBranches, branchIds, fromInclusive, limit).stream()
                .map(event -> new UpcomingEvent(event.id(), event.title(), event.startAt()))
                .toList();
    }
}
