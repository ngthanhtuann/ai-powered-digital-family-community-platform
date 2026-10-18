package com.familyconnect.media;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MediaUploadService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PDF_SIZE = 10L * 1024 * 1024;

    private final Path storagePath;

    // Chỉ dùng cho spike/demo.
    // Implementation thật sẽ lưu metadata vào bảng media.
    private final Map<UUID, StoredMedia> registry = new ConcurrentHashMap<>();

    public MediaUploadService() throws IOException {
        String configuredPath = System.getenv("MEDIA_STORAGE_PATH");

        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = "uploads";
        }

        storagePath = Paths.get(configuredPath)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(storagePath);
    }

    public StoredMedia save(
            MultipartFile file,
            String ownerType,
            UUID ownerId,
            String caption
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        if (ownerType == null || ownerType.isBlank()) {
            throw new IllegalArgumentException("ownerType không được để trống");
        }

        String normalizedOwnerType = ownerType.toUpperCase(Locale.ROOT);

        if (!normalizedOwnerType.equals("HERITAGE")
                && !normalizedOwnerType.equals("PERSON")) {
            throw new IllegalArgumentException(
                    "ownerType chỉ nhận HERITAGE hoặc PERSON"
            );
        }

        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId không được để trống");
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        String extension = getExtension(originalName);
        DetectedFile detected = detectFileType(file);

        if (!extension.equals(detected.extension())) {
            throw new UnsupportedFileException(
                    "Phần mở rộng không khớp nội dung thực tế của tệp"
            );
        }

        if (detected.extension().equals("pdf")) {
            if (!normalizedOwnerType.equals("HERITAGE")) {
                throw new UnsupportedFileException(
                        "PDF chỉ được phép khi ownerType = HERITAGE"
                );
            }

            if (file.getSize() > MAX_PDF_SIZE) {
                throw new FileTooLargeException(
                        "PDF vượt quá giới hạn 10 MB"
                );
            }
        } else if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new FileTooLargeException(
                    "Ảnh vượt quá giới hạn 5 MB"
            );
        }

        UUID id = UUID.randomUUID();

        String storageKey =
                UUID.randomUUID() + "." + detected.extension();

        Path destination = storagePath.resolve(storageKey).normalize();

        if (!destination.getParent().equals(storagePath)) {
            throw new IllegalArgumentException(
                    "Đường dẫn file không hợp lệ"
            );
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        }

        StoredMedia media = new StoredMedia(
                id,
                storageKey,
                normalizedOwnerType,
                ownerId,
                originalName,
                detected.mimeType(),
                file.getSize(),
                caption,
                destination
        );

        registry.put(id, media);

        return media;
    }

    public Optional<StoredMedia> findById(UUID id) {
        return Optional.ofNullable(registry.get(id));
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        String cleaned = originalName.replace('\\', '/');

        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }

        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        return cleaned;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');

        if (dot < 0 || dot == filename.length() - 1) {
            throw new UnsupportedFileException(
                    "File phải có phần mở rộng hợp lệ"
            );
        }

        String extension = filename.substring(dot + 1)
                .toLowerCase(Locale.ROOT);

        if (extension.equals("jpeg")) {
            extension = "jpg";
        }

        if (!extension.equals("jpg")
                && !extension.equals("png")
                && !extension.equals("webp")
                && !extension.equals("pdf")) {
            throw new UnsupportedFileException(
                    "Chỉ hỗ trợ JPG, PNG, WEBP hoặc PDF của Heritage"
            );
        }

        return extension;
    }

    private DetectedFile detectFileType(MultipartFile file)
            throws IOException {

        byte[] bytes = new byte[12];

        try (InputStream input = file.getInputStream()) {
            int count = input.read(bytes);

            if (count >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF) {
                return new DetectedFile("jpg", "image/jpeg");
            }

            if (count >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A) {
                return new DetectedFile("png", "image/png");
            }

            if (count >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P') {
                return new DetectedFile("webp", "image/webp");
            }

            if (count >= 5
                    && bytes[0] == '%'
                    && bytes[1] == 'P'
                    && bytes[2] == 'D'
                    && bytes[3] == 'F'
                    && bytes[4] == '-') {
                return new DetectedFile(
                        "pdf",
                        "application/pdf"
                );
            }
        }

        throw new UnsupportedFileException(
                "Không nhận diện được nội dung JPG, PNG, WEBP hoặc PDF"
        );
    }

    public record StoredMedia(
            UUID id,
            String storageKey,
            String ownerType,
            UUID ownerId,
            String fileName,
            String contentType,
            long fileSize,
            String caption,
            Path path
    ) {}

    private record DetectedFile(
            String extension,
            String mimeType
    ) {}

    public static class FileTooLargeException
            extends IllegalArgumentException {

        public FileTooLargeException(String message) {
            super(message);
        }
    }

    public static class UnsupportedFileException
            extends IllegalArgumentException {

        public UnsupportedFileException(String message) {
            super(message);
        }
    }
}