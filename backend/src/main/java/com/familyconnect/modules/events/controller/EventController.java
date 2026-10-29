package com.familyconnect.modules.events.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.events.dto.EventDto.*;
import com.familyconnect.modules.events.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** API /events và /participants theo community-events.yaml. */
@RestController
@RequestMapping("/api/v1")
public class EventController {
    private final EventService service;
    public EventController(EventService service) { this.service = service; }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<EventView>> create(@RequestParam(required = false) UUID familyId,
                                                          @Valid @RequestBody CreateEvent body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(familyId,body)));
    }

    @GetMapping("/events")
    public PageSuccess<EventView> list(@RequestParam(required = false) UUID familyId,
                                       @RequestParam(required = false) UUID branchId,
                                       @RequestParam(required = false, name = "from") OffsetDateTime from,
                                       @RequestParam(required = false, name = "to") OffsetDateTime to,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(familyId,branchId,from,to,page,size,sort);
    }

    @GetMapping("/events/{id}")
    public ApiResponse<EventView> get(@PathVariable UUID id) { return ApiResponse.ok(service.get(id)); }

    @PatchMapping("/events/{id}")
    public ApiResponse<EventView> update(@PathVariable UUID id, @RequestBody UpdateEvent body) {
        return ApiResponse.ok(service.update(id,body));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.empty();
    }

    @PutMapping("/events/{id}/rsvp")
    public ApiResponse<ParticipantView> rsvp(@PathVariable UUID id, @Valid @RequestBody RsvpRequest body) {
        return ApiResponse.ok(service.rsvp(id,body));
    }

    @GetMapping("/events/{id}/participants")
    public PageSuccess<ParticipantView> participants(@PathVariable UUID id,
                                                     @RequestParam(required = false) RsvpStatus rsvpStatus,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.participants(id,rsvpStatus,page,size,sort);
    }

    @PostMapping("/events/{id}/participants")
    public ResponseEntity<ApiResponse<ParticipantView>> addParticipant(
            @PathVariable UUID id, @Valid @RequestBody ParticipantRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(service.addParticipant(id,body)));
    }

    @DeleteMapping("/participants/{id}")
    public ApiResponse<Void> removeParticipant(@PathVariable UUID id) {
        service.removeParticipant(id);
        return ApiResponse.empty();
    }
}
