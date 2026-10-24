package com.familyconnect.media;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaUploadService uploadService;
    private final MediaAccess mediaAccess;

    public MediaController(MediaUploadService uploadService, MediaAccess mediaAccess) {
        this.uploadService = uploadService;
        this.mediaAccess = mediaAccess;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerType") String ownerType,
            @RequestParam("ownerId") UUID ownerId,
            @RequestParam(value = "caption", required = false) String caption
    ) {
        // Chỉ thành viên đã xác minh của gia đình sở hữu được tải tệp lên; lỗi trả qua GlobalExceptionHandler.
        mediaAccess.requireAccess(ownerType, ownerId);
        try {
            MediaUploadService.StoredMedia media =
                    uploadService.save(
                            file,
                            ownerType,
                            ownerId,
                            caption
                    );

            MediaData data = new MediaData(
                    media.id(),
                    media.ownerType(),
                    media.ownerId(),
                    media.fileName(),
                    media.contentType(),
                    media.fileSize(),
                    "/api/v1/media/" + media.id() + "/content",
                    media.caption()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new SuccessResponse<>(true, data));

        } catch (MediaUploadService.FileTooLargeException e) {
            return error(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_001",
                    e.getMessage()
            );

        } catch (MediaUploadService.UnsupportedFileException e) {
            return error(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "FILE_002",
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "VALID_001",
                    e.getMessage()
            );

        } catch (IOException e) {
            return error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SERVER_001",
                    "Không thể lưu tệp"
            );
        }
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<?> getContent(@PathVariable UUID id)
            throws IOException {

        MediaUploadService.StoredMedia media =
                uploadService.findById(id).orElse(null);

        if (media == null || !Files.isRegularFile(media.path())) {
            return error(
                    HttpStatus.NOT_FOUND,
                    "NOT_FOUND_001",
                    "Không tìm thấy tài nguyên"
            );
        }
        mediaAccess.requireAccess(media.ownerType(), media.ownerId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        media.contentType()
                ))
                .body(Files.readAllBytes(media.path()));
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(
                        false,
                        new ErrorDetail(
                                code,
                                message,
                                List.of()
                        )
                )
        );
    }

    public record SuccessResponse<T>(
            boolean success,
            T data
    ) {}

    public record MediaData(
            UUID id,
            String ownerType,
            UUID ownerId,
            String fileName,
            String contentType,
            long fileSize,
            String contentUrl,
            String caption
    ) {}

    public record ErrorResponse(
            boolean success,
            ErrorDetail error
    ) {}

    public record ErrorDetail(
            String code,
            String message,
            List<Object> details
    ) {}
}