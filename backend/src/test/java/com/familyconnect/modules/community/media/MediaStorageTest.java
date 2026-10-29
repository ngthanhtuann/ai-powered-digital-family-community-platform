package com.familyconnect.modules.community.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaStorageTest {
    @TempDir Path directory;

    private static byte[] png(long size) {
        byte[] bytes = new byte[(int) size];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    @Test void validPngUsesUuidStorageKeyAndRealMime() throws IOException {
        MediaStorage storage = new MediaStorage(directory.toString());
        var stored = storage.store(file("folder\\anh.png", png(256)), false);
        assertEquals("anh.png", stored.fileName());
        assertEquals("image/png", stored.mimeType());
        assertTrue(stored.storageKey().matches("[0-9a-f-]{36}\\.png"));
        assertDoesNotThrow(() -> UUID.fromString(stored.storageKey().substring(0, 36)));
        assertTrue(Files.isRegularFile(storage.resolve(stored.storageKey())));
    }

    @Test void fakePngContentIsRejectedWith415() throws IOException {
        MediaStorage storage = new MediaStorage(directory.toString());
        ApiException ex = assertThrows(ApiException.class,
            () -> storage.store(file("fake.png", "not an image".getBytes()), false));
        assertEquals(ErrorCode.FILE_002, ex.code());
    }

    @Test void validPngTooLargeIsRejectedWith413() throws IOException {
        MediaStorage storage = new MediaStorage(directory.toString());
        ApiException ex = assertThrows(ApiException.class,
            () -> storage.store(file("large.png", png(MediaStorage.MAX_IMAGE_BYTES + 1)), false));
        assertEquals(ErrorCode.FILE_001, ex.code());
    }

    @Test void pdfOnlyForHeritageUpToTenMegabytes() throws IOException {
        MediaStorage storage = new MediaStorage(directory.toString());
        byte[] bytes = "%PDF-1.4".getBytes();
        assertEquals("application/pdf", storage.store(file("history.pdf", bytes), true).mimeType());
        ApiException denied = assertThrows(ApiException.class,
            () -> storage.store(file("history.pdf", bytes), false));
        assertEquals(ErrorCode.FILE_002, denied.code());
        byte[] huge = Arrays.copyOf(bytes, (int) MediaStorage.MAX_PDF_BYTES + 1);
        ApiException oversized = assertThrows(ApiException.class,
            () -> storage.store(file("history.pdf", huge), true));
        assertEquals(ErrorCode.FILE_001, oversized.code());
    }

    @Test void extensionMustMatchMagicBytes() throws IOException {
        MediaStorage storage = new MediaStorage(directory.toString());
        ApiException ex = assertThrows(ApiException.class,
            () -> storage.store(file("renamed.jpg", png(32)), false));
        assertEquals(ErrorCode.FILE_002, ex.code());
    }
}
