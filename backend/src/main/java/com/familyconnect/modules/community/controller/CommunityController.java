package com.familyconnect.modules.community.controller;

import com.familyconnect.modules.community.dto.CommunityDto.*;
import com.familyconnect.modules.community.service.CommunityPlatformPort;
import com.familyconnect.modules.community.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnBean(CommunityPlatformPort.class)
public class CommunityController {
    private final CommunityService service;
    public CommunityController(CommunityService service) { this.service = service; }

    @PostMapping("/posts")
    public ResponseEntity<Success<PostView>> createPost(
        @RequestParam(required=false) UUID familyId, @Valid @RequestBody CreatePost body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Success.of(service.createPost(familyId,body)));
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
    public Success<PostView> post(@PathVariable UUID id) {
        return Success.of(service.post(id));
    }
    @PatchMapping("/posts/{id}")
    public Success<PostView> updatePost(@PathVariable UUID id, @Valid @RequestBody UpdatePost body) {
        return Success.of(service.updatePost(id,body));
    }
    @DeleteMapping("/posts/{id}")
    public Success<Void> deletePost(@PathVariable UUID id) {
        service.deletePost(id);
        return Success.of(null);
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
    public ResponseEntity<Success<CommentView>> createComment(
        @PathVariable UUID id, @Valid @RequestBody CommentInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Success.of(service.createComment(id,body)));
    }
    @PatchMapping("/comments/{id}")
    public Success<CommentView> updateComment(@PathVariable UUID id, @Valid @RequestBody CommentInput body) {
        return Success.of(service.updateComment(id,body));
    }
    @DeleteMapping("/comments/{id}")
    public Success<Void> deleteComment(@PathVariable UUID id) {
        service.deleteComment(id);
        return Success.of(null);
    }

    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<Success<ReactionView>> react(
        @PathVariable UUID id, @Valid @RequestBody ReactionInput body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Success.of(service.react(id,body)));
    }
    @DeleteMapping("/posts/{id}/reactions")
    public Success<Void> removeReaction(@PathVariable UUID id) {
        service.removeReaction(id);
        return Success.of(null);
    }
}
