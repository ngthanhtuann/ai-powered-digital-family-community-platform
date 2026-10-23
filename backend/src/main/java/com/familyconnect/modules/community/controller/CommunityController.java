package com.familyconnect.modules.community.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.community.dto.CommunityDto.*;
import com.familyconnect.modules.community.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommunityController {
    private final CommunityService service;
    public CommunityController(CommunityService service) { this.service = service; }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostView>> createPost(
        @RequestParam(required=false) UUID familyId, @Valid @RequestBody CreatePost body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createPost(familyId,body)));
    }
    @GetMapping("/posts")
    public PageSuccess<PostView> posts(
        @RequestParam(required=false) UUID familyId,
        @RequestParam(required=false) UUID branchId,
        @RequestParam(required=false) PostType type,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt,desc") String sort) {
        return service.posts(familyId,branchId,type,page,size,sort);
    }
    @GetMapping("/posts/{id}")
    public ApiResponse<PostView> post(@PathVariable UUID id) {
        return ApiResponse.ok(service.post(id));
    }
    @PatchMapping("/posts/{id}")
    public ApiResponse<PostView> updatePost(@PathVariable UUID id, @Valid @RequestBody UpdatePost body) {
        return ApiResponse.ok(service.updatePost(id,body));
    }
    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable UUID id) {
        service.deletePost(id);
        return ApiResponse.empty();
    }

    @GetMapping("/posts/{id}/comments")
    public PageSuccess<CommentView> comments(
        @PathVariable UUID id,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt,desc") String sort) {
        return service.comments(id,page,size,sort);
    }
    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<ApiResponse<CommentView>> createComment(
        @PathVariable UUID id, @Valid @RequestBody CommentInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.createComment(id,body)));
    }
    @PatchMapping("/comments/{id}")
    public ApiResponse<CommentView> updateComment(@PathVariable UUID id, @Valid @RequestBody CommentInput body) {
        return ApiResponse.ok(service.updateComment(id,body));
    }
    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable UUID id) {
        service.deleteComment(id);
        return ApiResponse.empty();
    }

    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<ApiResponse<ReactionView>> react(
        @PathVariable UUID id, @Valid @RequestBody ReactionInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.react(id,body)));
    }
    @DeleteMapping("/posts/{id}/reactions")
    public ApiResponse<Void> removeReaction(@PathVariable UUID id) {
        service.removeReaction(id);
        return ApiResponse.empty();
    }
}
