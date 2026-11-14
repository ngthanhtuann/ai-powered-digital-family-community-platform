package com.familyconnect.modules.ai;

import com.familyconnect.modules.ai.service.EmbeddingPipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /ai/recommendations (UC-AI-05) với PostgreSQL thật. Gợi ý dùng embedding đã lưu nên không được gọi Gemini.
 * Bao phủ: phạm vi gia đình/chi, ẩn trẻ dưới 16 tuổi với MEMBER, xếp hạng, nguồn ngoài quyền xem (403/404),
 * VALID_001, PERM_002, AUTH_002.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EmbeddingPipelineService embedding;

    private static String vector(float first) {
        float[] v = new float[768];
        Arrays.fill(v, 0.1f);
        v[0] = first;
        return Arrays.toString(v);
    }

    private String newUser() throws Exception {
        String email = "recommend." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    private ResultActions send(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("data").get("id").asText());
    }

    private UUID chunk(UUID familyId, UUID branchId, String type, UUID sourceId, float first) {
        UUID id = sourceId == null ? UUID.randomUUID() : sourceId;
        jdbc.update("""
                insert into ai_embedding_chunk(family_id, branch_id, source_type, source_id, chunk_no, content,
                                               embedding, content_hash)
                values (?, ?, ?, ?, 0, ?, CAST(? AS vector), 'hash')
                """, familyId, branchId, type, id, "Nội dung " + type, vector(first));
        return id;
    }

    private String member(UUID familyId, UUID branchId, String role) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "select id, ?, ?, ?, 'APPROVED' from users where email = ?", familyId, branchId, role, email);
        return token(email);
    }

    private ResultActions recommend(String token, String type, UUID contextId) throws Exception {
        return mvc.perform(get("/api/v1/ai/recommendations").param("contextType", type)
                .param("contextId", contextId.toString()).header("Authorization", "Bearer " + token));
    }

    private List<UUID> ids(ResultActions result) throws Exception {
        JsonNode items = json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("data").get("recommendations");
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : items) {
            assertThat(item.get("reason").asText()).isNotBlank();
            ids.add(UUID.fromString(item.get("sourceId").asText()));
        }
        return ids;
    }

    @Test
    void recommendationsStayInScopeRankBySimilarityAndNeverCallGemini() throws Exception {
        String owner = token(newUser());
        UUID family = id(send("/api/v1/families", owner, Map.of("name", "Dòng họ thử")).andExpect(status().isCreated()));
        UUID root = id(send("/api/v1/families/" + family + "/branches", owner, Map.of("name", "Chi trưởng")));
        Map<String, Object> childBranch = new HashMap<>(Map.of("name", "Chi con"));
        childBranch.put("parentBranchId", root.toString());
        UUID child = id(send("/api/v1/families/" + family + "/branches", owner, childBranch));
        UUID sibling = id(send("/api/v1/families/" + family + "/branches", owner, Map.of("name", "Chi thứ")));
        Map<String, Object> kid = new HashMap<>(Map.of("fullName", "Bé Na", "gender", "FEMALE", "birthDate", "2018-05-01"));
        UUID minor = id(send("/api/v1/families/" + family + "/persons", owner, kid).andExpect(status().isCreated()));

        UUID context = chunk(family, null, "POST", null, 0.1f);
        UUID closest = chunk(family, child, "EVENT", null, 0.1f);
        UUID farther = chunk(family, root, "STORY", null, 5f);
        UUID siblingPost = chunk(family, sibling, "POST", null, 0.1f);
        chunk(family, null, "PERSON", minor, 0.1f);
        UUID otherFamilyPost = chunk(UUID.randomUUID(), null, "POST", null, 0.1f);

        String childMember = member(family, child, "MEMBER");
        // Thành viên chi con: gần nhất đứng trước; không có chính nguồn, chi khác, trẻ em, gia đình khác.
        assertThat(ids(recommend(childMember, "POST", context))).containsExactly(closest, farther);
        // Người tạo gia đình (trưởng chi toàn gia đình) thấy mọi chi và cả hồ sơ trẻ em.
        assertThat(ids(recommend(owner, "POST", context))).contains(closest, farther, siblingPost, minor)
                .doesNotContain(context, otherFamilyPost);

        // Nguồn ngoài quyền xem: chi khác, trẻ em bị ẩn, gia đình khác → 403; không có trong chỉ mục → 404.
        recommend(childMember, "POST", siblingPost).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        recommend(childMember, "PERSON", minor).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        recommend(childMember, "POST", otherFamilyPost).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        recommend(childMember, "POST", UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND_001"));

        verify(embedding, never()).createEmbedding(anyString());
    }

    @Test
    void rejectsInvalidInputUnverifiedAndAnonymous() throws Exception {
        String owner = token(newUser());
        send("/api/v1/families", owner, Map.of("name", "Dòng họ thử")).andExpect(status().isCreated());
        UUID any = UUID.randomUUID();

        recommend(owner, "COMMENT", any).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
        mvc.perform(get("/api/v1/ai/recommendations").param("contextType", "POST").param("contextId", any.toString())
                        .param("limit", "11").header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));
        recommend(token(newUser()), "POST", any).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_002"));
        mvc.perform(get("/api/v1/ai/recommendations").param("contextType", "POST").param("contextId", any.toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }
}
