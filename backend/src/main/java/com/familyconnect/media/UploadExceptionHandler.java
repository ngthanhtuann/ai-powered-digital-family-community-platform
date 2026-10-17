package com.familyconnect.media;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Spring chặn tệp vượt max-file-size / max-request-size trước khi vào controller.
 * Trả lỗi theo chuẩn conventions.md (413 FILE_001) thay vì body rỗng.
 */
@RestControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<MediaController.ErrorResponse> handleTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                new MediaController.ErrorResponse(
                        false,
                        new MediaController.ErrorDetail(
                                "FILE_001",
                                "Tệp vượt quá dung lượng cho phép",
                                List.of()
                        )
                )
        );
    }
}
