package com.familyconnect.modules.ai;

import com.familyconnect.modules.ai.service.EmbeddingPipelineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /ai/search (UC-AI-01) với PostgreSQL thật; embedding được giả lập nên không gọi Gemini, mỗi test tự rollback.
 * Bao phủ: lọc family_id (IDOR), phạm vi chi BR-AI-13/BR-COM-03, ẩn trẻ dưới 16 tuổi với MEMBER, PERM_002, VALID_001.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SemanticSearchApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";
    private static final float[] VECTOR = new float[768];

    static {
        Arrays.fill(VECTOR, 0.1f);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EmbeddingPipelineService embedding;

    @BeforeEach
    void fakeEmbedding() {
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.createEmbedding(anyString())).thenReturn(VECTOR);
    }

    private String newUser() throws Exception {
        String email = "search." + UUID.randomUUID() + "@example.com";
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
        return mvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private UUID id(ResultActions result) throws Exception {
        return UUID.fromString(json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("data").get("id").asText());
    }

    private Set<UUID> search(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/ai/search").param("q", "thông tin gia đình")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<UUID> ids = new HashSet<>();
        for (JsonNode result : json.readTree(body).get("data").get("results")) {
            ids.add(UUID.fromString(result.get("sourceId").asText()));
        }
        return ids;
    }

    private UUID chunk(UUID familyId, UUID branchId, String type, UUID sourceId) {
        UUID id = sourceId == null ? UUID.randomUUID() : sourceId;
        jdbc.update("""
                insert into ai_embedding_chunk(family_id, branch_id, source_type, source_id, chunk_no, content,
                                               embedding, content_hash)
                values (?, ?, ?, ?, 0, ?, CAST(? AS vector), 'hash')
                """, familyId, branchId, type, id, "Nội dung " + type, Arrays.toString(VECTOR));
        return id;
    }

    private String member(UUID familyId, UUID branchId, String role) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, branch_id, role, status) "
                + "select id, ?, ?, ?, 'APPROVED' from users where email = ?", familyId, branchId, role, email);
        return token(email);
    }

    @Test
    void resultsFollowFamilyBranchScopeAndHideMinorsFromMembers() throws Exception {
        String owner = token(newUser());
        UUID family = id(send("/api/v1/families", owner, Map.of("name", "Dòng họ thử")).andExpect(status().isCreated()));
        UUID root = id(send("/api/v1/families/" + family + "/branches", owner, Map.of("name", "Chi trưởng")));
        Map<String, Object> childBranch = new HashMap<>(Map.of("name", "Chi con"));
        childBranch.put("parentBranchId", root.toString());
        UUID child = id(send("/api/v1/families/" + family + "/branches", owner, childBranch));
        UUID sibling = id(send("/api/v1/families/" + family + "/branches", owner, Map.of("name", "Chi thứ")));
        Map<String, Object> kid = new HashMap<>(Map.of("fullName", "Bé Na", "gender", "FEMALE", "birthDate", "2018-05-01"));
        UUID minor = id(send("/api/v1/families/" + family + "/persons", owner, kid).andExpect(status().isCreated()));

        UUID familyPost = chunk(family, null, "POST", null);
        UUID rootPost = chunk(family, root, "POST", null);
        UUID childEvent = chunk(family, child, "EVENT", null);
        UUID siblingPost = chunk(family, sibling, "POST", null);
        chunk(family, null, "PERSON", minor);
        UUID otherFamilyPost = chunk(UUID.randomUUID(), null, "POST", null);

        // Thành viên chi con: thấy toàn gia đình, chi con và chi tổ tiên; không thấy chi khác, trẻ em, gia đình khác.
        assertThat(search(member(family, child, "MEMBER")))
                .contains(familyPost, rootPost, childEvent)
                .doesNotContain(siblingPost, minor, otherFamilyPost);
        // Thành viên chưa thuộc chi nào: chỉ nội dung toàn gia đình.
        assertThat(search(member(family, null, "MEMBER")))
                .contains(familyPost)
                .doesNotContain(rootPost, childEvent, siblingPost, minor);
        // Trưởng chi trưởng: thấy chi mình và chi con, kể cả trẻ em; không thấy chi khác.
        assertThat(search(member(family, root, "BRANCH_ADMIN")))
                .contains(familyPost, rootPost, childEvent, minor)
                .doesNotContain(siblingPost, otherFamilyPost);
        // Người tạo gia đình là trưởng chi toàn gia đình: thấy mọi chi.
        assertThat(search(owner)).contains(familyPost, rootPost, childEvent, siblingPost, minor)
                .doesNotContain(otherFamilyPost);
    }

    @Test
    void rejectsUnverifiedOtherFamilyAndInvalidQuery() throws Exception {
        String owner = token(newUser());
        UUID family = id(send("/api/v1/families", owner, Map.of("name", "Dòng họ thử")).andExpect(status().isCreated()));
        String otherOwner = token(newUser());
        send("/api/v1/families", otherOwner, Map.of("name", "Gia đình khác")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/ai/search").param("q", "giỗ tổ")
                        .header("Authorization", "Bearer " + token(newUser())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_002"));
        mvc.perform(get("/api/v1/ai/search").param("q", "giỗ tổ").param("familyId", family.toString())
                        .header("Authorization", "Bearer " + otherOwner))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERM_001"));
        mvc.perform(get("/api/v1/ai/search").param("q", " ").header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));
        mvc.perform(get("/api/v1/ai/search").param("q", "giỗ tổ").param("size", "101")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALID_001"));
        mvc.perform(get("/api/v1/ai/search").param("q", "giỗ tổ"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }
}
