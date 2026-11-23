package com.familyconnect.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-AUTH-15, BR-AUTH-24 (SCRUM-139): mã đặt lại mật khẩu dùng một lần; dữ liệu giả, mỗi test tự rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";
    private static final String NEW_PASSWORD = "Matkhaumoi456";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String newUser() throws Exception {
        String email = "reset." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String token(String email) throws Exception {
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", UUID.class, email);
    }

    private void member(String email, UUID familyId, UUID branchId, String role) {
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) values (?, ?, ?, ?, 'APPROVED')",
                userId(email), familyId, branchId, role);
    }

    private ResultActions issue(String token, String email) throws Exception {
        return mvc.perform(post("/api/v1/users/" + userId(email) + "/password-reset-codes").header("Authorization", "Bearer " + token));
    }

    private ResultActions reset(String email, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "code", code, "newPassword", NEW_PASSWORD))));
    }

    /** Trưởng họ mới (tạo gia đình qua API), trả về [email, familyId]. */
    private Object[] head() throws Exception {
        String email = newUser();
        String body = mvc.perform(post("/api/v1/families").header("Authorization", "Bearer " + token(email))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "Dòng họ thử"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Object[]{email, UUID.fromString(json.readTree(body).get("data").get("id").asText())};
    }

    @Test
    void familyHeadIssuesOneTimeCodeAndMemberResetsPassword() throws Exception {
        Object[] h = head();
        String headToken = token((String) h[0]);
        String memberEmail = newUser();
        member(memberEmail, (UUID) h[1], null, "MEMBER");

        String body = issue(headToken, memberEmail).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = json.readTree(body).get("data");
        String code = data.get("code").asText();
        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(data.get("expiresAt").asText()).isNotBlank();
        // Chỉ lưu băm, nhật ký không có mã.
        assertThat(jdbc.queryForObject("select code_hash from password_reset_code where user_id = ?", String.class,
                userId(memberEmail))).isNotEqualTo(code).startsWith("$2");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where detail::text like ?", Integer.class, "%" + code + "%"))
                .isZero();

        reset(memberEmail, "ZZZZZZZZ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("code"));
        reset(memberEmail, code.toLowerCase()).andExpect(status().isOk());
        login(memberEmail, NEW_PASSWORD).andExpect(status().isOk());
        reset(memberEmail, code).andExpect(status().isBadRequest()); // dùng một lần
        reset("khong.co." + UUID.randomUUID() + "@example.com", code).andExpect(status().isBadRequest());
    }

    @Test
    void fiveWrongAttemptsDiscardTheCode() throws Exception {
        Object[] h = head();
        String memberEmail = newUser();
        member(memberEmail, (UUID) h[1], null, "MEMBER");
        issue(token((String) h[0]), memberEmail).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            reset(memberEmail, "WRONG" + i).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select deleted_at is not null and failed_attempts = 5 from password_reset_code where user_id = ?",
                Boolean.class, userId(memberEmail))).isTrue();
    }

    @Test
    void onlyFamilyHeadOrSystemAdminIssueWithinTheirScope() throws Exception {
        Object[] h = head();
        String headEmail = (String) h[0];
        UUID familyId = (UUID) h[1];
        String headToken = token(headEmail);
        String coHeadEmail = newUser();
        member(coHeadEmail, familyId, null, "BRANCH_ADMIN");
        String branchHeadEmail = newUser();
        String branchBody = mvc.perform(post("/api/v1/families/" + familyId + "/branches").header("Authorization", "Bearer " + headToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "Chi thử"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID branch = UUID.fromString(json.readTree(branchBody).get("data").get("id").asText());
        member(branchHeadEmail, familyId, branch, "BRANCH_ADMIN");
        String memberEmail = newUser();
        member(memberEmail, familyId, branch, "MEMBER");
        Object[] other = head();

        issue(headToken, headEmail).andExpect(status().isForbidden());                 // chính mình
        issue(headToken, coHeadEmail).andExpect(status().isForbidden());               // trưởng họ khác
        issue(headToken, (String) other[0]).andExpect(status().isNotFound());          // dòng họ khác
        issue(token(branchHeadEmail), memberEmail).andExpect(status().isForbidden());  // trưởng chi
        issue(token(memberEmail), branchHeadEmail).andExpect(status().isForbidden());  // thành viên
        issue(headToken, branchHeadEmail).andExpect(status().isCreated());

        String adminEmail = newUser();
        jdbc.update("insert into user_role(user_id, role_id) select ?, id from role where code = 'SYSTEM_ADMIN'", userId(adminEmail));
        issue(token(adminEmail), coHeadEmail).andExpect(status().isCreated());
    }
}
