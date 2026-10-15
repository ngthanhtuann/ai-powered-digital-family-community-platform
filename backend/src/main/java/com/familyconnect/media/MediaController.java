package com.familyconnect.media;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaUploadService uploadService;

    public MediaController(MediaUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            String storageKey = uploadService.saveImage(file);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new UploadResponse(storageKey, "Upload thành công")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(e.getMessage())
            );
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Không thể lưu file")
            );
        }
    }

    @GetMapping("/{storageKey}/content")
    public ResponseEntity<?> getContent(@PathVariable String storageKey)
            throws IOException {

        if (storageKey.contains("/") || storageKey.contains("\\")
                || storageKey.contains("..")) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Tên file không hợp lệ")
            );
        }

        String configuredPath = System.getenv("MEDIA_STORAGE_PATH");
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = "uploads";
        }

        Path storagePath = Paths.get(configuredPath)
                .toAbsolutePath().normalize();
        Path filePath = storagePath.resolve(storageKey).normalize();

        if (!filePath.getParent().equals(storagePath)
                || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(Files.readAllBytes(filePath));
    }

    public record UploadResponse(String storageKey, String message) {}
    public record ErrorResponse(String message) {}
}