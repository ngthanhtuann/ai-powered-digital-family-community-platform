package com.familyconnect.common.api;

/** Response thành công một đối tượng: {"success": true, "data": ...} (conventions.md mục 2.1). */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }

    /** Thao tác không trả dữ liệu (ví dụ xóa): data = null. */
    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null);
    }
}
