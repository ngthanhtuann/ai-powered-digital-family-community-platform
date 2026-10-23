package com.familyconnect.common.security;

import com.familyconnect.common.api.ApiError;
import com.familyconnect.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi 401, 403 do Spring Security chặn trước khi vào controller cũng theo định dạng chuẩn
 * (conventions.md mục 2.2): thiếu hoặc sai token là AUTH_002, tài khoản bị khóa là AUTH_003,
 * không đủ vai trò là PERM_001.
 */
@Component
public class SecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> {
            boolean locked = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.ACCOUNT_LOCKED_ATTRIBUTE));
            write(response, locked ? ErrorCode.AUTH_003 : ErrorCode.AUTH_002);
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, ErrorCode.PERM_001);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code));
    }
}
