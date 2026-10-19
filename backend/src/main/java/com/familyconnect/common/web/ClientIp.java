package com.familyconnect.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Địa chỉ IP của người gọi. Hiện lấy địa chỉ kết nối trực tiếp; khi chạy sau Nginx (SCRUM-96)
 * bật server.forward-headers-strategy để Spring đọc X-Forwarded-For từ proxy tin cậy.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static Optional<String> current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.ofNullable(of(attrs.getRequest()));
        }
        return Optional.empty();
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
