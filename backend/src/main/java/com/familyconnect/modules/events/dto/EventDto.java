package com.familyconnect.modules.events.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class EventDto {
    private EventDto() {}

    public enum RsvpStatus { GOING, NOT_GOING, MAYBE }

    public record CreateEvent(@NotBlank @Size(max = 255) String title,
                              @Size(max = 5000) String description,
                              @NotNull OffsetDateTime startAt,
                              OffsetDateTime endAt,
                              @Size(max = 255) String location,
                              UUID branchId) {}

    /** PATCH phân biệt trường không gửi với trường được gửi null. */
    public static final class UpdateEvent {
        private String title;
        private String description;
        private OffsetDateTime startAt;
        private OffsetDateTime endAt;
        private String location;
        private UUID branchId;
        private boolean titleSet, descriptionSet, startSet, endSet, locationSet, branchSet;

        public String title() { return title; }
        public String description() { return description; }
        public OffsetDateTime startAt() { return startAt; }
        public OffsetDateTime endAt() { return endAt; }
        public String location() { return location; }
        public UUID branchId() { return branchId; }
        public boolean titleSet() { return titleSet; }
        public boolean descriptionSet() { return descriptionSet; }
        public boolean startSet() { return startSet; }
        public boolean endSet() { return endSet; }
        public boolean locationSet() { return locationSet; }
        public boolean branchSet() { return branchSet; }
        @JsonSetter("title") public void setTitle(String v) { title = v; titleSet = true; }
        @JsonSetter("description") public void setDescription(String v) { description = v; descriptionSet = true; }
        @JsonSetter("startAt") public void setStartAt(OffsetDateTime v) { startAt = v; startSet = true; }
        @JsonSetter("endAt") public void setEndAt(OffsetDateTime v) { endAt = v; endSet = true; }
        @JsonSetter("location") public void setLocation(String v) { location = v; locationSet = true; }
        @JsonSetter("branchId") public void setBranchId(UUID v) { branchId = v; branchSet = true; }
    }

    public record RsvpRequest(@NotNull RsvpStatus rsvpStatus) {}
    public record ParticipantRequest(@NotNull UUID userId, RsvpStatus rsvpStatus) {}

    public record RsvpCounts(long going, long notGoing, long maybe) {}

    public record EventView(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                            UUID familyId, UUID branchId, String title, String description,
                            OffsetDateTime startAt, OffsetDateTime endAt, String location,
                            UUID createdBy, RsvpStatus myRsvpStatus, RsvpCounts rsvpCounts) {}

    public record ParticipantView(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                  UUID eventId, UUID userId, RsvpStatus rsvpStatus, String displayName) {}

    public record Meta(int page, int size, long total) {}
    public record PageSuccess<T>(boolean success, List<T> data, Meta meta) {
        public static <T> PageSuccess<T> of(List<T> data, int page, int size, long total) {
            return new PageSuccess<>(true, data, new Meta(page, size, total));
        }
    }
}
