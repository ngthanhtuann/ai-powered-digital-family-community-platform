package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.CreateRequest;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.JoinRequestData;
import com.familyconnect.modules.auth.dto.JoinRequestDtos.ReviewRequest;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import com.familyconnect.modules.auth.service.JoinRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Xin tham gia gia đình và duyệt (UC-AUTH-06, 07). Quyền và phạm vi kiểm ở JoinRequestService. */
@RestController
@RequestMapping("/api/v1/join-requests")
@Tag(name = "Membership")
public class JoinRequestController {

    private final JoinRequestService service;

    public JoinRequestController(JoinRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createJoinRequest", summary = "Gửi yêu cầu tham gia gia đình (UC-AUTH-06)")
    public ApiResponse<JoinRequestData> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(CurrentUser.require().id(), request));
    }

    @GetMapping
    @Operation(operationId = "listJoinRequests", summary = "Danh sách yêu cầu tham gia cần duyệt (UC-AUTH-07)")
    public PageResponse<JoinRequestData> list(@RequestParam(required = false) VerificationStatus status,
                                              @RequestParam(required = false) UUID familyId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.list(CurrentUser.require().id(), status, familyId, page, size, sort);
    }

    @PatchMapping("/{id}")
    @Operation(operationId = "reviewJoinRequest", summary = "Duyệt hoặc từ chối yêu cầu tham gia (UC-AUTH-07)")
    public ApiResponse<JoinRequestData> review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.ok(service.review(CurrentUser.require().id(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "cancelJoinRequest", summary = "Hủy yêu cầu tham gia của tôi (UC-AUTH-06)")
    public ApiResponse<Void> cancel(@PathVariable UUID id) {
        service.cancel(CurrentUser.require().id(), id);
        return ApiResponse.empty();
    }
}
