package com.familyconnect.modules.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-53 với PostgreSQL thật, mỗi test tự rollback: announcement (BR-COM-09, BR-COM-03), notification của
 * từng người (BR-EVT-05) và thư viện ảnh sự kiện. Bao phủ nhóm phân quyền và IDOR của test-plan.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnnouncementNotificationApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";
    private static final byte[] PNG = png();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    private static byte[] png() {
        byte[] bytes = Arrays.copyOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, 200);
        return bytes;
    }

    private String newUser(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String token(String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(data(result.andExpect(status().isCreated())).get("id").asText());
    }

    /** Thành viên đã duyệt của gia đình (chi tùy chọn), dữ liệu giả. */
    private String member(UUID family, UUID branch) throws Exception {
        String email = newUser("member");
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "select id, ?, ?, 'MEMBER', 'APPROVED' from users where email = ?", family, branch, email);
        return token(email);
    }

    @Test
    void announcementReachesMembersInScopeAndOnlyManagersWrite() throws Exception {
        String head = token(newUser("head"));
        UUID family = id(call(post("/api/v1/families"), head, Map.of("name", "Dòng họ A")));
        UUID branchA = id(call(post("/api/v1/families/" + family + "/branches"), head, Map.of("name", "Chi A")));
        UUID branchB = id(call(post("/api/v1/families/" + family + "/branches"), head, Map.of("name", "Chi B")));
        String inA = member(family, branchA);
        String inB = member(family, branchB);
        String outsider = token(newUser("outsider"));
        id(call(post("/api/v1/families"), outsider, Map.of("name", "Dòng họ B")));

        call(post("/api/v1/announcements"), inA, Map.of("title", "x", "content", "y"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));

        Map<String, Object> scoped = new HashMap<>(Map.of("title", "Họp chi A", "content", "Chủ nhật 9 giờ.",
                "isPinned", true));
        scoped.put("branchId", branchA.toString());
        UUID announcement = id(call(post("/api/v1/announcements"), head, scoped)
                .andExpect(jsonPath("$.data.isPinned").value(true)));

        call(get("/api/v1/announcements/" + announcement), inA, null).andExpect(status().isOk());
        call(get("/api/v1/announcements/" + announcement), inB, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        call(get("/api/v1/announcements/" + announcement), outsider, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));

        // Chỉ người trong phạm vi chi nhận thông báo; mỗi người một bản ghi riêng.
        JsonNode mine = data(call(get("/api/v1/notifications"), inA, null).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("type").asText()).isEqualTo("ANNOUNCEMENT");
        assertThat(mine.get(0).get("isRead").asBoolean()).isFalse();
        assertThat(data(call(get("/api/v1/notifications"), inB, null).andExpect(status().isOk()))).isEmpty();
        assertThat(data(call(get("/api/v1/notifications"), head, null).andExpect(status().isOk()))).isEmpty();

        UUID notification = UUID.fromString(mine.get(0).get("id").asText());
        call(patch("/api/v1/notifications/" + notification), outsider, Map.of("isRead", true))
                .andExpect(status().isNotFound());
        call(patch("/api/v1/notifications/" + notification), inA, Map.of("isRead", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    void eventGalleryFollowsEventReadScope() throws Exception {
        String head = token(newUser("head"));
        UUID family = id(call(post("/api/v1/families"), head, Map.of("name", "Dòng họ A")));
        String inFamily = member(family, null);
        String outsider = token(newUser("outsider"));
        id(call(post("/api/v1/families"), outsider, Map.of("name", "Dòng họ B")));
        UUID event = id(call(post("/api/v1/events"), head, Map.of("title", "Giỗ tổ",
                "startAt", OffsetDateTime.now().plusDays(3).toString())));

        mvc.perform(multipart("/api/v1/events/" + event + "/media")
                        .file(new MockMultipartFile("file", "anh.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + inFamily))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/events/" + event + "/media")
                        .file(new MockMultipartFile("file", "anh.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));

        call(get("/api/v1/events/" + event + "/media"), head, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(1));
        call(get("/api/v1/events/" + event + "/media"), outsider, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
    }
}
