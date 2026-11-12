package com.familyconnect.modules.auth;

import com.familyconnect.modules.auth.api.SystemConfigQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * SCRUM-94, UC-AUTH-12, BR-AUTH-18: cấu hình hệ thống. Nhóm test phân quyền, quy ước API (VALID_001, NOT_FOUND_001,
 * PERM_001), audit log, và hành vi đổi ngay không cần khởi động lại.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemConfigApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpServletResponse send(HttpMethod method, String url, String token, Object body) throws Exception {
        var req = request(method, url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(req).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse res) throws Exception {
        return json.readTree(res.getContentAsString(StandardCharsets.UTF_8));
    }

    private String code(MockHttpServletResponse res) throws Exception {
        return body(res).at("/error/code").asText();
    }

    private String newUser(String name) throws Exception {
        String email = "config." + UUID.randomUUID() + "@example.com";
        send(HttpMethod.POST, "/api/v1/users", null, Map.of("email", email, "password", PASSWORD,
                "fullName", name, "dateOfBirth", "1990-01-01"));
        return email;
    }

    private JsonNode login(String email) throws Exception {
        return body(send(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", PASSWORD)))
                .get("data");
    }

    private String token(String email) throws Exception {
        return login(email).get("accessToken").asText();
    }

    private String admin() throws Exception {
        String email = newUser("Quản trị");
        jdbc.update("insert into user_role(user_id, role_id) select id, (select id from role where code = 'SYSTEM_ADMIN')"
                + " from users where email = ?", email);
        return token(email);
    }

    private MockHttpServletResponse put(String token, String key, String value) throws Exception {
        return send(HttpMethod.PUT, "/api/v1/admin/configs/" + key, token, Map.of("value", value));
    }

    @Test
    void adminListsSeededKeysMatchingDocumentedDefaults() throws Exception {
        MockHttpServletResponse res = send(HttpMethod.GET, "/api/v1/admin/configs", admin(), null);
        assertThat(res.getStatus()).isEqualTo(200);
        JsonNode data = body(res).get("data");
        assertThat(body(res).at("/meta/total").asInt()).isEqualTo(data.size()).isEqualTo(10);
        Map<String, String> defaults = new HashMap<>();
        data.forEach(item -> {
            defaults.put(item.get("key").asText(), item.get("defaultValue").asText());
            assertThat(item.get("group").asText()).isNotBlank();
            assertThat(item.get("description").asText()).isNotBlank();
            assertThat(item.get("valueType").asText()).isIn("INTEGER", "BOOLEAN", "STRING");
        });
        // Migration V20 và SystemConfigQuery.DEFAULTS (auth.md mục 5.3) phải giống nhau.
        assertThat(defaults).isEqualTo(SystemConfigQuery.DEFAULTS);
    }

    @Test
    void onlySystemAdminCanReadOrChange() throws Exception {
        String member = token(newUser("Thành viên"));
        assertThat(code(send(HttpMethod.GET, "/api/v1/admin/configs", member, null))).isEqualTo("PERM_001");
        assertThat(code(put(member, "app.name", "Khác"))).isEqualTo("PERM_001");
        assertThat(send(HttpMethod.GET, "/api/v1/admin/configs", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void invalidValuesUnknownKeyAndAuditLog() throws Exception {
        String admin = admin();
        for (String[] bad : new String[][] {
                {"ai.chat.limit_per_hour", "0"}, {"ai.chat.limit_per_hour", "201"}, {"ai.chat.limit_per_hour", "abc"},
                {"ai.chat.enabled", "yes"}, {"upload.max_image_mb", "6"}, {"upload.allowed_image_types", "gif"},
                {"upload.allowed_image_types", ""}, {"membership.approval_mode", "auto-ish"},
                {"auth.access_token_minutes", "4"}, {"auth.refresh_token_days", "31"}, {"app.name", "  "}}) {
            MockHttpServletResponse res = put(admin, bad[0], bad[1]);
            assertThat(res.getStatus()).as(bad[0] + "=" + bad[1]).isEqualTo(400);
            assertThat(code(res)).isEqualTo("VALID_001");
        }
        MockHttpServletResponse unknown = put(admin, "khong.ton.tai", "1");
        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(code(unknown)).isEqualTo("NOT_FOUND_001");

        MockHttpServletResponse ok = put(admin, "upload.allowed_image_types", " webp, JPG ");
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(body(ok).at("/data/value").asText()).isEqualTo("jpg,webp");
        assertThat(body(ok).at("/data/updatedBy").asText()).isNotBlank();
        Map<String, Object> log = jdbc.queryForMap("""
                select action, target_type, detail->>'key' k, detail->>'oldValue' old_value, detail->>'newValue' new_value
                from audit_log where action = 'CONFIG_CHANGED' order by created_at desc limit 1""");
        assertThat(log).containsEntry("target_type", "SYSTEM_CONFIG").containsEntry("k", "upload.allowed_image_types")
                .containsEntry("old_value", "jpg,png,webp").containsEntry("new_value", "jpg,webp");

        // Khôi phục mặc định: gửi lại defaultValue.
        assertThat(body(put(admin, "upload.allowed_image_types", "jpg,png,webp")).at("/data/value").asText())
                .isEqualTo("jpg,png,webp");
    }

    @Test
    void changesApplyWithoutRestart() throws Exception {
        String admin = admin();
        String email = newUser("Người dùng");
        assertThat(login(email).get("expiresIn").asLong()).isEqualTo(15 * 60);
        put(admin, "auth.access_token_minutes", "5");
        assertThat(login(email).get("expiresIn").asLong()).isEqualTo(5 * 60);

        // AUTO: yêu cầu xin vào gia đình được duyệt ngay (UC-AUTH-06 4a).
        String head = token(newUser("Trưởng họ"));
        String familyId = body(send(HttpMethod.POST, "/api/v1/families", head, Map.of("name", "Họ Cấu Hình")))
                .at("/data/id").asText();
        put(admin, "membership.approval_mode", "AUTO");
        String applicant = token(newUser("Người xin"));
        MockHttpServletResponse joined = send(HttpMethod.POST, "/api/v1/join-requests", applicant,
                Map.of("code", familyId, "message", "Xin vào"));
        assertThat(joined.getStatus()).isEqualTo(201);
        assertThat(body(joined).at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'JOIN_APPROVED' and detail->>'auto' = 'true'",
                Integer.class)).isEqualTo(1);

        // Tắt chatbot: trả AI_002 trước khi gọi LLM.
        put(admin, "ai.chat.enabled", "false");
        MockHttpServletResponse chat = send(HttpMethod.POST, "/api/v1/ai/chat", applicant, Map.of("question", "Ông nội tôi là ai?"));
        assertThat(code(chat)).isEqualTo("AI_002");
    }
}
