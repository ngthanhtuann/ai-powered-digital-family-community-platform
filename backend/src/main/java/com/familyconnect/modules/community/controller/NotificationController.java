package com.familyconnect.modules.community.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.community.dto.CommunityDto.PageSuccess;
import com.familyconnect.modules.community.repository.JdbcNotificationStore.Notification;
import com.familyconnect.modules.community.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    public record MarkReadRequest(@NotNull Boolean isRead) {}
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public PageSuccess<Notification> list(@RequestParam(required = false) Boolean isRead,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(isRead,page,size,sort);
    }

    @PatchMapping("/{id}")
    public ApiResponse<Notification> read(@PathVariable UUID id, @Valid @RequestBody MarkReadRequest body) {
        return ApiResponse.ok(service.markRead(id,body.isRead()));
    }
}
