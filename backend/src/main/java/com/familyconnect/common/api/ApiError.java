package com.familyconnect.common.api;

import com.familyconnect.common.exception.ErrorCode;

import java.util.List;

/** Response lỗi: {"success": false, "error": {code, message, details[]}} (conventions.md mục 2.2). */
public record ApiError(boolean success, Body error) {

    public record Body(String code, String message, List<FieldError> details) {}

    public record FieldError(String field, String message) {}

    public static ApiError of(String code, String message, List<FieldError> details) {
        return new ApiError(false, new Body(code, message, details == null ? List.of() : details));
    }

    public static ApiError of(ErrorCode code) {
        return of(code.name(), code.defaultMessage(), List.of());
    }
}
