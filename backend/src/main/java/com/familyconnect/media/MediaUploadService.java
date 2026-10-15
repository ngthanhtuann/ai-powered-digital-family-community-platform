package com.familyconnect.media;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaUploadService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final Path storagePath;

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

    public String saveImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File vượt quá giới hạn 5 MB");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Tên file không hợp lệ");
        }

        String extension = getAllowedExtension(originalName);
        String detectedType = detectImageType(file);

        if (!extension.equals(detectedType)) {
            throw new IllegalArgumentException(
                    "Định dạng file không hợp lệ hoặc không khớp nội dung"
            );
        }

        String storageKey = UUID.randomUUID() + "." + extension;
        Path destination = storagePath.resolve(storageKey).normalize();

        if (!destination.getParent().equals(storagePath)) {
            throw new IllegalArgumentException("Đường dẫn file không hợp lệ");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        }

        return storageKey;
    }

    private String getAllowedExtension(String filename) {
        int dot = filename.lastIndexOf('.');

        if (dot < 0) {
            throw new IllegalArgumentException("File phải có phần mở rộng");
        }

        String extension = filename.substring(dot + 1)
                .toLowerCase(Locale.ROOT);

        if (!extension.equals("jpg")
                && !extension.equals("jpeg")
                && !extension.equals("png")
                && !extension.equals("webp")) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP"
            );
        }

        if (extension.equals("jpeg")) {
            return "jpg";
        }

        return extension;
    }

    private String detectImageType(MultipartFile file) throws IOException {
        byte[] bytes = new byte[12];

        try (InputStream input = file.getInputStream()) {
            int count = input.read(bytes);

            if (count >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF) {
                return "jpg";
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
                return "png";
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
                return "webp";
            }
        }

        throw new IllegalArgumentException(
                "Không nhận diện được nội dung ảnh JPG, PNG hoặc WEBP"
        );
    }
}