package com.familyconnect.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tạo và kiểm tra access token (JWT HS256). Claims: sub (mã người dùng), roles, iat, exp (auth.md mục 4.2).
 * Token không chứa familyId, BRANCH_ADMIN hay cờ đã xác minh.
 */
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < JwtProperties.MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Thiếu biến môi trường JWT_SECRET hoặc ngắn hơn " + JwtProperties.MIN_SECRET_BYTES
                            + " ký tự. Xem .env.example.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenMinutes());
        this.clock = clock;
    }

    public String createAccessToken(UUID userId, List<String> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Thời hạn access token tính bằng giây (trường expiresIn của API). */
    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /** Trả người dùng nếu token đúng chữ ký và còn hạn; ngược lại rỗng. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            List<?> rawRoles = claims.get(ROLES_CLAIM, List.class);
            List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
            return Optional.of(new AuthenticatedUser(userId, roles));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
