package com.familyconnect.modules.community.service;

import org.springframework.http.HttpStatus;

public class CommunityFailure extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;

    public CommunityFailure(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }
    public CommunityFailure(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public static CommunityFailure invalid(String field, String message) {
        return new CommunityFailure(HttpStatus.BAD_REQUEST, "VALID_001", message, field);
    }
    public static CommunityFailure forbidden() {
        return new CommunityFailure(HttpStatus.FORBIDDEN, "PERM_001", "Không đủ quyền truy cập");
    }
    public static CommunityFailure notFound() {
        return new CommunityFailure(HttpStatus.NOT_FOUND, "NOT_FOUND_001", "Không tìm thấy tài nguyên");
    }
}
