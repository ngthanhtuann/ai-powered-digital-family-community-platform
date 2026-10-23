package com.familyconnect.modules.auth;

import com.familyconnect.common.security.JwtProperties;
import com.familyconnect.common.security.JwtService;
import com.familyconnect.modules.auth.api.UserDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm tra API Auth qua HTTP (MockMvc) với PostgreSQL thật: docker compose up -d db.
 * Mỗi test chạy trong giao dịch và tự rollback, không để lại dữ liệu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    private String uniqueEmail() {
        return "test." + UUID.randomUUID() + "@example.com";
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private void register(String email) throws Exception {
        postJson("/api/v1/users", Map.of(
                "email", email, "password", PASSWORD, "fullName", "Nguyễn Văn An",
                "phone", "0901234567", "dateOfBirth", "1990-05-12"))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email) throws Exception {
        String body = postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    @Test
    void registerReturns201WithUserAndNoPassword() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/users", Map.of(
                "email", email.toUpperCase(), "password", PASSWORD, "fullName", "Nguyễn Văn An",
                "dateOfBirth", "1990-05-12"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.phone").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void registerInvalidDataReturnsValid001WithDetails() throws Exception {
        postJson("/api/v1/users", Map.of(
                "email", "khong-phai-email", "password", "ngan", "fullName", "An", "dateOfBirth", "1990-05-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALID_001"))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("email")))
                .andExpect(jsonPath("$.error.details[*].field", hasItem("password")));
    }

    @Test
    void registerSameEmailTwiceReturnsAuth004() throws Exception {
        String email = uniqueEmail();
        register(email);
        postJson("/api/v1/users", Map.of(
                "email", email, "password", PASSWORD, "fullName", "An", "dateOfBirth", "1990-05-12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void loginReturnsTokensUserAndMembership() throws Exception {
        String email = uniqueEmail();
        register(email);
        postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.membership.status").value("NONE"))
                .andExpect(jsonPath("$.data.membership.canCreateFamily").value(true));
    }

    @Test
    void emailWithSurroundingSpacesIsTrimmedOnRegisterAndLogin() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/users", Map.of(
                "email", "  " + email.toUpperCase() + " ", "password", PASSWORD, "fullName", "Nguyễn Văn An",
                "dateOfBirth", "1990-05-12"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(email));
        postJson("/api/v1/auth/login", Map.of("email", " " + email + "  ", "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    void loginWrongPasswordReturnsAuth001() throws Exception {
        String email = uniqueEmail();
        register(email);
        postJson("/api/v1/auth/login", Map.of("email", email, "password", "SaiMatKhau1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_001"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void loginRateLimitReturns429WithRetryAfter() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", Map.of("email", email, "password", "sai"))
                    .andExpect(status().isUnauthorized());
        }
        postJson("/api/v1/auth/login", Map.of("email", email, "password", "sai"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_001"));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsAuth002() throws Exception {
        mvc.perform(get("/api/v1/users/me/membership"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void expiredAccessTokenIsRejectedWithAuth002() throws Exception {
        String email = uniqueEmail();
        register(email);
        String userId = login(email).get("user").get("id").asText();
        // Token ký đúng khóa nhưng phát hành 16 phút trước (thời hạn 15 phút).
        JwtService past = new JwtService(jwtProperties,
                Clock.fixed(Instant.now().minus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        String expired = past.createAccessToken(UUID.fromString(userId), List.of("MEMBER"));

        mvc.perform(get("/api/v1/users/me/membership").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void validAccessTokenReachesProtectedEndpoint() throws Exception {
        String email = uniqueEmail();
        register(email);
        String accessToken = login(email).get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me/membership").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NONE"));
    }

    @Test
    void getMyProfileReturnsOwnUserWithoutPassword() throws Exception {
        String email = uniqueEmail();
        register(email);
        String accessToken = login(email).get("accessToken").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.data.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void userDirectoryReturnsNamesOfActiveUsersOnly() throws Exception {
        String activeEmail = uniqueEmail();
        String deletedEmail = uniqueEmail();
        register(activeEmail);
        register(deletedEmail);
        entityManager.flush();
        UUID activeId = jdbc.queryForObject("select id from users where email = ?", UUID.class, activeEmail);
        UUID deletedId = jdbc.queryForObject("select id from users where email = ?", UUID.class, deletedEmail);
        jdbc.update("update users set deleted_at = now() where id = ?", deletedId);
        entityManager.clear();

        Map<UUID, String> names = userDirectory.getFullNames(List.of(activeId, deletedId, UUID.randomUUID()));

        assertThat(names).containsOnlyKeys(activeId);
        assertThat(names.get(activeId)).isEqualTo("Nguyễn Văn An");
    }

    @Test
    void lockedAccountIsBlockedOnNextRequestWithAuth003() throws Exception {
        String email = uniqueEmail();
        register(email);
        String accessToken = login(email).get("accessToken").asText();
        // Quản trị khóa tài khoản ở một giao dịch khác (mô phỏng bằng SQL trực tiếp).
        entityManager.flush();
        jdbc.update("update users set status = 'LOCKED', lock_reason = 'Thử nghiệm' where email = ?", email);
        // Mỗi request thật có bộ đệm Hibernate riêng; xóa bộ đệm của test để không đọc lại bản cũ.
        entityManager.clear();

        mvc.perform(get("/api/v1/users/me/membership").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
        postJson("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    void swaggerApiDocsArePublicAndListAuthEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.operationId").value("login"))
                .andExpect(jsonPath("$.paths['/api/v1/users'].post.operationId").value("registerUser"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokenAndOldOneCannotBeReused() throws Exception {
        String email = uniqueEmail();
        register(email);
        String refreshToken = login(email).get("refreshToken").asText();

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void logoutRevokesRefreshTokens() throws Exception {
        String email = uniqueEmail();
        register(email);
        JsonNode tokens = login(email);

        mvc.perform(delete("/api/v1/auth/sessions/current")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", tokens.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void changePasswordThenLoginWithNewPassword() throws Exception {
        String email = uniqueEmail();
        register(email);
        String accessToken = login(email).get("accessToken").asText();

        mvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", "Sai12345", "newPassword", "Matkhaumoi456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("currentPassword"));

        mvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "Matkhaumoi456"))))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/login", Map.of("email", email, "password", "Matkhaumoi456"))
                .andExpect(status().isOk());
    }
}
