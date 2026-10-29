package com.familyconnect.modules.community.media;

import com.familyconnect.common.api.ApiError;
import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.dto.CommunityDto.PageSuccess;
import com.familyconnect.modules.community.media.MediaService.MediaView;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/** Endpoint theo docs/04-api/openapi/community-events.yaml. */
@RestController
@RequestMapping("/api/v1")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) { this.service = service; }

    @PostMapping(path = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaView>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerType") String ownerType,
            @RequestParam("ownerId") UUID ownerId,
            @RequestParam(value = "caption", required = false) String caption) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(service.upload(ownerType, ownerId, caption, file)));
    }

    @PostMapping(path = "/posts/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaView>> uploadPost(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.uploadPost(id, file)));
    }

    @GetMapping("/posts/{id}/media")
    public PageSuccess<MediaView> postMedia(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.listPostMedia(id, page, size, sort);
    }

    @GetMapping("/media/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id) {
        MediaService.MediaContent media = service.content(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(media.contentType()))
            .body(new FileSystemResource(media.path()));
    }

    @DeleteMapping("/media/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.empty();
    }
}
