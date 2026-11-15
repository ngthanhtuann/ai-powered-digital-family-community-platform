package com.familyconnect.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình JWT (application.yml: app.jwt.*). Khóa ký lấy từ biến môi trường JWT_SECRET. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int accessTokenMinutes, int refreshTokenDays) {

    /** HS256 cần khóa tối thiểu 256 bit. */
    public static final int MIN_SECRET_BYTES = 32;
}
