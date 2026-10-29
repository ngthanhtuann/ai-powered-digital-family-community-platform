package com.familyconnect.modules.events.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.events.dto.EventDto.*;
import com.familyconnect.modules.events.repository.EventStore;
import com.familyconnect.modules.events.repository.EventStore.Event;
import com.familyconnect.modules.events.repository.EventStore.Participant;
import com.familyconnect.modules.events.service.EventAccess.Actor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** [4-05] UC-EVT-01..03: sự kiện, danh sách, RSVP và người tham dự. */
@Service
public class EventService {
    private final EventStore store;
    private final EventAccess access;
    private final Clock clock;

    public EventService(EventStore store, EventAccess access, Clock clock) {
        this.store = store;
        this.access = access;
        this.clock = clock;
    }

    private static void validate(String title, String description, OffsetDateTime start,
                                 OffsetDateTime end, String location) {
        if (title == null || title.isBlank() || title.length() > 255)
            throw ApiException.invalidField("title", "Tên sự kiện phải có từ 1 đến 255 ký tự");
        if (description != null && description.length() > 5000)
            throw ApiException.invalidField("description", "Mô tả tối đa 5000 ký tự");
        if (location != null && location.length() > 255)
            throw ApiException.invalidField("location", "Địa điểm tối đa 255 ký tự");
        if (start == null) throw ApiException.invalidField("startAt", "Thiếu thời gian bắt đầu");
        if (end != null && !end.isAfter(start))
            throw ApiException.invalidField("endAt", "Kết thúc phải sau thời điểm bắt đầu");
    }

    private static void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.invalidField("page", "Phân trang không hợp lệ");
    }

    private static String sort(String value, boolean participants) {
        String[] parts = (value == null ? "createdAt,desc" : value).split(",",-1);
        if (parts.length != 2 || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc")))
            throw ApiException.invalidField("sort", "Giá trị sort không hợp lệ");
        String column = switch (parts[0]) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "startAt" -> participants ? null : "start_at";
            case "title" -> participants ? null : "title";
            case "rsvpStatus" -> participants ? "rsvp_status" : null;
            default -> null;
        };
        if (column == null) throw ApiException.invalidField("sort", "Trường sắp xếp không hợp lệ");
        String dir = parts[1].equalsIgnoreCase("asc") ? "ASC" : "DESC";
        return column + " " + dir + (column.equals("id") ? "" : ", id " + dir);
    }

    private Event readable(Actor actor, UUID id) {
        Event event = store.findEvent(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        access.requireRead(actor,event.familyId(),event.branchId());
        return event;
    }

    private List<EventView> views(List<Event> events, Actor actor) {
        if (events.isEmpty()) return List.of();
        List<UUID> ids = events.stream().map(Event::id).toList();
        Map<UUID,RsvpCounts> counts = store.counts(ids);
        Map<UUID,RsvpStatus> mine = store.myStatuses(ids,actor.id());
        return events.stream().map(e -> new EventView(e.id(),e.createdAt(),e.updatedAt(),
            e.familyId(),e.branchId(),e.title(),e.description(),e.startAt(),e.endAt(),e.location(),
            e.createdBy(),mine.get(e.id()),counts.getOrDefault(e.id(),new RsvpCounts(0,0,0)))).toList();
    }

    private EventView view(Event event, Actor actor) { return views(List.of(event),actor).get(0); }

    private List<ParticipantView> participants(List<Participant> items) {
        if (items.isEmpty()) return List.of();
        Map<UUID,String> names = access.names(items.stream().map(Participant::userId).collect(Collectors.toSet()));
        return items.stream().map(p -> new ParticipantView(p.id(),p.createdAt(),p.updatedAt(),
            p.eventId(),p.userId(),p.status(),names.getOrDefault(p.userId(),"Thành viên đã rời hệ thống"))).toList();
    }

    @Transactional
    public EventView create(UUID requestedFamily, CreateEvent input) {
        Actor actor = access.actor();
        if (input == null) throw ApiException.invalidField("title", "Thiếu thông tin sự kiện");
        UUID family = access.family(actor,requestedFamily);
        validate(input.title(),input.description(),input.startAt(),input.endAt(),input.location());
        access.requirePublish(actor,family,input.branchId());
        Event saved = store.insertEvent(family,input.branchId(),input.title(),input.description(),
            input.startAt(),input.endAt(),input.location(),actor.id());
        access.eventChanged(saved,false);
        return view(saved,actor);
    }

    @Transactional(readOnly=true)
    public PageSuccess<EventView> list(UUID requestedFamily, UUID branch, OffsetDateTime from, OffsetDateTime to,
                                       int page, int size, String ordering) {
        Actor actor = access.actor();
        UUID family = access.family(actor,requestedFamily);
        pagination(page,size);
        if (from != null && to != null && from.isAfter(to))
            throw ApiException.invalidField("from", "Khoảng thời gian không hợp lệ");
        boolean all = actor.role() == EffectiveRole.SYSTEM_ADMIN ||
            (actor.role() == EffectiveRole.BRANCH_ADMIN && actor.branchId() == null);
        List<UUID> visible = all ? List.of() : store.eventBranchIds(family).stream()
            .filter(id -> access.canRead(actor,family,id)).toList();
        var found = store.listEvents(family,all,visible,branch,from,to,page,size,sort(ordering,false));
        return PageSuccess.of(views(found.items(),actor),page,size,found.total());
    }

    @Transactional(readOnly=true)
    public EventView get(UUID id) {
        Actor actor = access.actor();
        return view(readable(actor,id),actor);
    }

    @Transactional
    public EventView update(UUID id, UpdateEvent input) {
        Actor actor = access.actor();
        Event old = readable(actor,id);
        access.requireManage(actor,old);
        if (input == null) throw ApiException.invalidField("event", "Thiếu thông tin cập nhật");
        String title = input.titleSet() ? input.title() : old.title();
        String description = input.descriptionSet() ? input.description() : old.description();
        OffsetDateTime start = input.startSet() ? input.startAt() : old.startAt();
        OffsetDateTime end = input.endSet() ? input.endAt() : old.endAt();
        String location = input.locationSet() ? input.location() : old.location();
        UUID branch = input.branchSet() ? input.branchId() : old.branchId();
        validate(title,description,start,end,location);
        access.requirePublish(actor,old.familyId(),branch);
        Event saved = store.updateEvent(id,branch,title,description,start,end,location,actor.id());
        access.eventChanged(saved,false);
        return view(saved,actor);
    }

    @Transactional
    public void delete(UUID id) {
        Actor actor = access.actor();
        Event event = readable(actor,id);
        access.requireManage(actor,event);
        store.deleteEvent(id,actor.id());
        access.eventDeleted(event,actor);
        access.eventChanged(event,true);
    }

    @Transactional
    public ParticipantView rsvp(UUID eventId, RsvpRequest input) {
        Actor actor = access.actor();
        Event event = readable(actor,eventId);
        if (input == null || input.rsvpStatus() == null) throw ApiException.invalidField("rsvpStatus", "Thiếu RSVP");
        if (!event.startAt().toInstant().isAfter(clock.instant())) throw new ApiException(ErrorCode.EVT_001);
        Participant saved = store.upsertRsvp(eventId,actor.id(),input.rsvpStatus(),actor.id());
        return participants(List.of(saved)).get(0);
    }

    @Transactional(readOnly=true)
    public PageSuccess<ParticipantView> participants(UUID eventId, RsvpStatus status,
                                                     int page, int size, String ordering) {
        Actor actor = access.actor();
        readable(actor,eventId);
        pagination(page,size);
        var found = store.participants(eventId,status,page,size,sort(ordering,true));
        return PageSuccess.of(participants(found.items()),page,size,found.total());
    }

    @Transactional
    public ParticipantView addParticipant(UUID eventId, ParticipantRequest input) {
        Actor actor = access.actor();
        Event event = readable(actor,eventId);
        access.requireManage(actor,event);
        if (input == null || input.userId() == null) throw ApiException.invalidField("userId", "Thiếu thành viên");
        access.requireFamilyUser(input.userId(),event.familyId());
        RsvpStatus status = input.rsvpStatus() == null ? RsvpStatus.MAYBE : input.rsvpStatus();
        try {
            return participants(List.of(store.insertParticipant(eventId,input.userId(),status,actor.id()))).get(0);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.CONFLICT_001);
        }
    }

    @Transactional
    public void removeParticipant(UUID participantId) {
        Actor actor = access.actor();
        Participant found = store.findParticipant(participantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        Event event = readable(actor,found.eventId());
        access.requireManage(actor,event);
        store.deleteParticipant(participantId,actor.id());
    }
}
