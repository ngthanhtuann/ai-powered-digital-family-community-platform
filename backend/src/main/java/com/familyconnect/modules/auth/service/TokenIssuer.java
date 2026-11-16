package com.familyconnect.modules.auth.service;

import com.familyconnect.common.security.JwtService;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import com.familyconnect.modules.auth.dto.AuthResponses.TokenData;
import com.familyconnect.modules.auth.entity.RefreshToken;
import com.familyconnect.modules.auth.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Cấp cặp token: access token JWT và refresh token ngẫu nhiên (không phải JWT).
 * Refresh token chỉ lưu hash SHA-256 (auth.md mục 4.2).
 */
@Component
public class TokenIssuer {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SystemConfigQuery config;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenIssuer(JwtService jwtService, RefreshTokenRepository refreshTokenRepository,
                       SystemConfigQuery config, Clock clock) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.config = config;
        this.clock = clock;
    }

    /**
     * Gọi trong giao dịch của service để refresh token được lưu cùng lúc. Thời hạn hai token đọc từ cấu hình hệ thống
     * (auth.access_token_minutes, auth.refresh_token_days; SCRUM-94), áp dụng cho token cấp sau khi đổi.
     */
    public TokenData issue(UUID userId, List<String> roles) {
        Duration accessTtl = Duration.ofMinutes(config.getInt(SystemConfigQuery.AUTH_ACCESS_TOKEN_MINUTES));
        String accessToken = jwtService.createAccessToken(userId, roles, accessTtl);
        String refreshToken = newRefreshToken();
        OffsetDateTime expiresAt = now().plus(Duration.ofDays(config.getInt(SystemConfigQuery.AUTH_REFRESH_TOKEN_DAYS)));
        refreshTokenRepository.save(new RefreshToken(userId, hash(refreshToken), expiresAt));
        return TokenData.bearer(accessToken, refreshToken, accessTtl.toSeconds());
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    public static String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
