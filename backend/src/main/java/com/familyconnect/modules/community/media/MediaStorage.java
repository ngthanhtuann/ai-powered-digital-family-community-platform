package com.familyconnect.modules.community.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** Docker named volume `uploads` theo ADR-upload; không cung cấp URL tệp trực tiếp. */
@Component
public class MediaStorage {
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_PDF_BYTES = 10L * 1024 * 1024;

    private final Path root;

    public MediaStorage(@Value("${MEDIA_STORAGE_PATH:uploads}") String path) throws IOException {
        this.root = Path.of(path).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public record StoredFile(String storageKey, String fileName, String mimeType, long size) {}

    public StoredFile store(MultipartFile file, boolean heritage) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.invalidField("file", "Tệp không được để trống");
        if (file.getSize() > MAX_PDF_BYTES) throw new ApiException(ErrorCode.FILE_001);

        String fileName = safeName(file.getOriginalFilename());
        String ext = extension(fileName);
        Detected detected = detect(file);
        if (!ext.equals(detected.ext()) || ("pdf".equals(ext) && !heritage)) {
            throw new ApiException(ErrorCode.FILE_002);
        }
        if (file.getSize() > ("pdf".equals(ext) ? MAX_PDF_BYTES : MAX_IMAGE_BYTES)) {
            throw new ApiException(ErrorCode.FILE_001);
        }

        String key = UUID.randomUUID() + "." + ext;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, resolve(key));
        }
        return new StoredFile(key, fileName, detected.mime(), file.getSize());
    }

    public Path resolve(String key) {
        Path destination = root.resolve(key).normalize();
        if (!destination.getParent().equals(root)) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
        return destination;
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) throw ApiException.invalidField("file", "Tên tệp không hợp lệ");
        String clean = name.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1);
        if (clean.isBlank() || clean.length() > 255) {
            throw ApiException.invalidField("file", "Tên tệp không hợp lệ");
        }
        return clean;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) throw new ApiException(ErrorCode.FILE_002);
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if ("jpeg".equals(ext)) ext = "jpg";
        if (!ext.equals("jpg") && !ext.equals("png") && !ext.equals("webp") && !ext.equals("pdf")) {
            throw new ApiException(ErrorCode.FILE_002);
        }
        return ext;
    }

    private record Detected(String ext, String mime) {}

    /** Nội dung tệp (magic bytes), không tin Content-Type do client cung cấp. */
    private static Detected detect(MultipartFile file) throws IOException {
        byte[] b;
        try (InputStream in = file.getInputStream()) { b = in.readNBytes(12); }
        if (b.length >= 3 && (b[0] & 255) == 0xff && (b[1] & 255) == 0xd8 && (b[2] & 255) == 0xff) {
            return new Detected("jpg", "image/jpeg");
        }
        if (b.length >= 8 && (b[0] & 255) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) {
            return new Detected("png", "image/png");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Detected("webp", "image/webp");
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return new Detected("pdf", "application/pdf");
        }
        throw new ApiException(ErrorCode.FILE_002);
    }
}
