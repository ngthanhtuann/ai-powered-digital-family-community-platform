package com.familyconnect.modules.community.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.community.dto.CommunityDto.PageSuccess;
import com.familyconnect.modules.community.repository.JdbcAnnouncementStore.Announcement;
import com.familyconnect.modules.community.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {
    private final AnnouncementService service;
    public AnnouncementController(AnnouncementService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<Announcement>> create(@RequestParam(required=false) UUID familyId,
                @Valid @RequestBody AnnouncementService.Create body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(familyId,body)));
    }

    @GetMapping
    public PageSuccess<Announcement> list(@RequestParam(required=false) UUID familyId,
            @RequestParam(required=false) UUID branchId,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="createdAt,desc") String sort) {
        return service.list(familyId,branchId,page,size,sort);
    }

    @GetMapping("/{id}")
    public ApiResponse<Announcement> get(@PathVariable UUID id) { return ApiResponse.ok(service.get(id)); }

    @PatchMapping("/{id}")
    public ApiResponse<Announcement> update(@PathVariable UUID id,
                @Valid @RequestBody AnnouncementService.Update body) {
        return ApiResponse.ok(service.update(id,body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.empty();
    }
}
