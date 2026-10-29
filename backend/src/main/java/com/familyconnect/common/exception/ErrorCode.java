package com.familyconnect.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi dùng chung (conventions.md mục 3) và mã riêng của module auth (auth.md mục 6.2).
 * Module khác thêm mã của mình vào đây hoặc tạo ApiException với HttpStatus riêng.
 */
public enum ErrorCode {
    AUTH_001(HttpStatus.UNAUTHORIZED, "Sai email hoặc mật khẩu"),
    AUTH_002(HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn"),
    AUTH_003(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa"),
    AUTH_004(HttpStatus.CONFLICT, "Email đã được đăng ký"),
    AUTH_005(HttpStatus.CONFLICT, "Bạn đã thuộc một gia đình hoặc đang có yêu cầu chờ duyệt"),
    AUTH_006(HttpStatus.CONFLICT, "Yêu cầu tham gia đã được xử lý"),
    PERM_001(HttpStatus.FORBIDDEN, "Không đủ quyền truy cập tính năng này"),
    PERM_002(HttpStatus.FORBIDDEN, "Chưa được duyệt vào gia đình"),
    VALID_001(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ"),
    NOT_FOUND_001(HttpStatus.NOT_FOUND, "Tài nguyên không tồn tại"),
    CONFLICT_001(HttpStatus.CONFLICT, "Xung đột dữ liệu"),
    FILE_001(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp vượt quá dung lượng cho phép"),
    FILE_002(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng tệp không được hỗ trợ"),
    RATE_001(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau"),
    AI_001(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI hiện không khả dụng"),
    AI_002(HttpStatus.CONFLICT, "Tính năng AI hiện đang tắt"),
    SERVER_001(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại sau"),
    // Mã riêng của module Gia phả (genealogy.md mục 7.2, HTTP 409).
    GEN_001(HttpStatus.CONFLICT, "Gia đình còn thành viên, không xóa được"),
    GEN_002(HttpStatus.CONFLICT, "Chi còn thành viên hoặc chi con, không xóa được"),
    GEN_003(HttpStatus.CONFLICT, "Chi cha tạo vòng lặp"),
    GEN_004(HttpStatus.CONFLICT, "Thành viên còn quan hệ cha-mẹ-con hoặc hôn nhân, không xóa được"),
    GEN_012(HttpStatus.CONFLICT, "Người dưới 16 tuổi không có tài khoản đăng nhập riêng"),
    GEN_013(HttpStatus.CONFLICT, "Bạn đã thuộc một gia đình"),
    GEN_014(HttpStatus.CONFLICT, "Hồ sơ không thuộc gia đình của tài khoản cần gắn");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
