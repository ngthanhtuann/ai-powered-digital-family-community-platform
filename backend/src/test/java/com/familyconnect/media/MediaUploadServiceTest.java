package com.familyconnect.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUploadServiceTest {

    @TempDir
    Path storage;

    @Test
    void rejectsImagesAboveFiveMegabytesWithFile001() throws IOException {
        byte[] bytes = imageBytes(5 * 1024 * 1024 + 1);
        MockMultipartFile file = new MockMultipartFile("file", "family.png", "image/png", bytes);
        MediaUploadService service = new MediaUploadService(storage);

        assertThatThrownBy(() -> service.save(file, "HERITAGE", UUID.randomUUID(), null))
                .isInstanceOf(MediaUploadService.FileTooLargeException.class);
    }

    @Test
    void rejectsPdfAboveTenMegabytesWithFile001() throws IOException {
        byte[] bytes = new byte[10 * 1024 * 1024 + 1];
        bytes[0] = '%';
        bytes[1] = 'P';
        bytes[2] = 'D';
        bytes[3] = 'F';
        bytes[4] = '-';
        MockMultipartFile file = new MockMultipartFile("file", "family.pdf", "application/pdf", bytes);
        MediaUploadService service = new MediaUploadService(storage);

        assertThatThrownBy(() -> service.save(file, "HERITAGE", UUID.randomUUID(), null))
                .isInstanceOf(MediaUploadService.FileTooLargeException.class);
    }

    @Test
    void rejectsUnsupportedExtensionWithFile002() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "text".getBytes());
        MediaUploadService service = new MediaUploadService(storage);

        assertThatThrownBy(() -> service.save(file, "HERITAGE", UUID.randomUUID(), null))
                .isInstanceOf(MediaUploadService.UnsupportedFileException.class);
    }

    @Test
    void controllerMapsOversizeAndUnsupportedFileErrorsToContractCodes() throws IOException {
        MediaUploadService service = new MediaUploadService(storage);
        MediaController controller = new MediaController(service, org.mockito.Mockito.mock(MediaAccess.class));
        UUID familyId = UUID.randomUUID();

        var tooLarge = controller.upload(
                new MockMultipartFile("file", "large.png", "image/png", imageBytes(5 * 1024 * 1024 + 1)),
                "HERITAGE", familyId, null);
        var unsupported = controller.upload(
                new MockMultipartFile("file", "notes.txt", "text/plain", "text".getBytes()),
                "HERITAGE", familyId, null);

        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(((MediaController.ErrorResponse) tooLarge.getBody()).error().code()).isEqualTo("FILE_001");
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(((MediaController.ErrorResponse) unsupported.getBody()).error().code()).isEqualTo("FILE_002");
    }

    private static byte[] imageBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4e;
        bytes[3] = 0x47;
        bytes[4] = 0x0d;
        bytes[5] = 0x0a;
        bytes[6] = 0x1a;
        bytes[7] = 0x0a;
        return bytes;
    }
}
