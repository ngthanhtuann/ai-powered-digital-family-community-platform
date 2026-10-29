package com.familyconnect.modules.community.media;

import com.familyconnect.common.api.ApiError;
import com.familyconnect.common.exception.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Khi Spring chặn multipart trước controller vẫn trả 413 FILE_001. */
@RestControllerAdvice
public class MediaUploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(ErrorCode.FILE_001.status()).body(ApiError.of(ErrorCode.FILE_001));
    }
}
