package com.familyconnect.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-only-secret-for-unit-tests-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");

    private JwtService service(Clock clock) {
        return new JwtService(new JwtProperties(SECRET, 15, 7), clock);
    }

    @Test
    void validTokenIsParsedWithRoles() {
        JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();

        String token = jwt.createAccessToken(userId, List.of("MEMBER", "SYSTEM_ADMIN"));

        assertThat(jwt.parse(token)).hasValueSatisfying(user -> {
            assertThat(user.id()).isEqualTo(userId);
            assertThat(user.roles()).containsExactly("MEMBER", "SYSTEM_ADMIN");
            assertThat(user.isSystemAdmin()).isTrue();
        });
        assertThat(jwt.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(Clock.fixed(NOW, ZoneOffset.UTC)).createAccessToken(UUID.randomUUID(), List.of("MEMBER"));
        JwtService later = service(Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        JwtService other = new JwtService(new JwtProperties("another-secret-that-is-long-enough-0123456789", 15, 7),
                Clock.fixed(NOW, ZoneOffset.UTC));
        String token = other.createAccessToken(UUID.randomUUID(), List.of("MEMBER"));

        assertThat(service(Clock.fixed(NOW, ZoneOffset.UTC)).parse(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(service(Clock.systemUTC()).parse("khong-phai-jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("ngan", 15, 7), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
