package com.familyconnect.common.exception;

import com.familyconnect.common.api.ApiError;
import com.familyconnect.common.api.ApiError.FieldError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Đổi mọi lỗi thành JSON chuẩn {success:false, error:{code,message,details}} (conventions.md mục 2.2).
 * Lỗi có sẵn của Spring MVC (JSON sai, sai method, thiếu tham số, tệp quá lớn...) đi qua
 * ResponseEntityExceptionHandler rồi được đổi body ở handleExceptionInternal.
 * Đặt thứ tự thấp nhất để handler riêng của từng module (nếu có) được ưu tiên.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.code().status());
        if (ex.retryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        return builder.body(ApiError.of(ex.code().name(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.PERM_001, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.AUTH_002, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Lỗi không mong đợi", ex);
        return build(ErrorCode.SERVER_001, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALID_001.name(), ErrorCode.VALID_001.defaultMessage(), details);
        return ResponseEntity.status(ErrorCode.VALID_001.status()).headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, @Nullable Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        ErrorCode code = switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND_001;
            case 413 -> ErrorCode.FILE_001;
            case 415 -> ErrorCode.FILE_002;
            default -> status.is4xxClientError() ? ErrorCode.VALID_001 : ErrorCode.SERVER_001;
        };
        if (code == ErrorCode.SERVER_001) {
            log.error("Lỗi Spring MVC", ex);
        }
        return ResponseEntity.status(status).headers(headers).body(ApiError.of(code));
    }

    private static ResponseEntity<ApiError> build(ErrorCode code, List<FieldError> details) {
        return ResponseEntity.status(code.status())
                .body(ApiError.of(code.name(), code.defaultMessage(), details));
    }
}
