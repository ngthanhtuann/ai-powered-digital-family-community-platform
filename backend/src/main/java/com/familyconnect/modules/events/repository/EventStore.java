package com.familyconnect.modules.events.repository;

import com.familyconnect.modules.events.dto.EventDto.RsvpCounts;
import com.familyconnect.modules.events.dto.EventDto.RsvpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EventStore {
    record Event(UUID id, UUID familyId, UUID branchId, String title, String description,
                 OffsetDateTime startAt, OffsetDateTime endAt, String location,
                 UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Participant(UUID id, UUID eventId, UUID userId, RsvpStatus status,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    record Page<T>(List<T> items, long total) {}

    Event insertEvent(UUID family, UUID branch, String title, String description,
                      OffsetDateTime start, OffsetDateTime end, String location, UUID actor);
    Optional<Event> findEvent(UUID id);
    List<UUID> eventBranchIds(UUID family);
    Page<Event> listEvents(UUID family, boolean allBranches, List<UUID> readableBranches,
                           UUID branch, OffsetDateTime from, OffsetDateTime to,
                           int page, int size, String order);
    Event updateEvent(UUID id, UUID branch, String title, String description,
                      OffsetDateTime start, OffsetDateTime end, String location, UUID actor);
    void deleteEvent(UUID id, UUID actor);

    Participant upsertRsvp(UUID eventId, UUID userId, RsvpStatus status, UUID actor);
    Participant insertParticipant(UUID eventId, UUID userId, RsvpStatus status, UUID actor);
    Optional<Participant> findParticipant(UUID id);
    Page<Participant> participants(UUID eventId, RsvpStatus status, int page, int size, String order);
    void deleteParticipant(UUID id, UUID actor);
    Map<UUID, RsvpCounts> counts(List<UUID> eventIds);
    Map<UUID, RsvpStatus> myStatuses(List<UUID> eventIds, UUID userId);
}
