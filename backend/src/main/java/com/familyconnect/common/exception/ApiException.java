package com.familyconnect.common.exception;

import com.familyconnect.common.api.ApiError.FieldError;

import java.util.List;

/** Lỗi nghiệp vụ; GlobalExceptionHandler đổi thành JSON theo conventions.md mục 2.2. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> details;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), List.of(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    private ApiException(ErrorCode code, String message, List<FieldError> details, Long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** 400 VALID_001 cho một trường cụ thể. */
    public static ApiException invalidField(String field, String message) {
        return new ApiException(ErrorCode.VALID_001, ErrorCode.VALID_001.defaultMessage(),
                List.of(new FieldError(field, message)), null);
    }

    /** 429 RATE_001 kèm header Retry-After. */
    public static ApiException tooManyRequests(long retryAfterSeconds) {
        return new ApiException(ErrorCode.RATE_001, ErrorCode.RATE_001.defaultMessage(), List.of(), retryAfterSeconds);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> details() {
        return details;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
