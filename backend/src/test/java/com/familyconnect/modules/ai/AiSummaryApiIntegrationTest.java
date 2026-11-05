package com.familyconnect.modules.ai;

import com.familyconnect.modules.ai.repository.AiSummaryCache;
import com.familyconnect.modules.ai.service.SummaryLlm;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /ai/summaries (UC-AI-04) với PostgreSQL thật; LLM giả lập nên không gọi Gemini, mỗi test tự rollback.
 * Bao phủ: cache theo nguồn và nội dung (ai.md mục 5.4), IDOR giữa gia đình, chưa xác minh (PERM_002), vô hiệu hóa cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiSummaryApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AiSummaryCache cache;
    @MockBean private SummaryLlm llm;

    @BeforeEach
    void fakeLlm() {
        when(llm.modelName()).thenReturn("model-gia");
        when(llm.summarize(anyString())).thenReturn("Tóm tắt giả từ nội dung nguồn.");
    }

    private String newUser() throws Exception {
        String email = "summary." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("data").get("accessToken").asText();
    }

    private ResultActions send(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private UUID created(ResultActions result) throws Exception {
        return UUID.fromString(data(result.andExpect(status().isCreated())).get("id").asText());
    }

    private ResultActions summarize(String token, String type, UUID id) throws Exception {
        return send("/api/v1/ai/summaries", token, Map.of("sourceType", type, "sourceId", id.toString()));
    }

    @Test
    void summaryIsCachedPerSourceAndContent() throws Exception {
        String owner = newUser();
        UUID family = created(send("/api/v1/families", owner, Map.of("name", "Dòng họ thử")));
        UUID story = created(send("/api/v1/heritage/stories", owner,
                Map.of("title", "Chuyện giả", "content", "Gia đình họp mặt vào tháng 3 tại nhà ông An.")));

        JsonNode first = data(summarize(owner, "STORY", story).andExpect(status().isOk()));
        assertThat(first.get("summary").asText()).isEqualTo("Tóm tắt giả từ nội dung nguồn.");
        assertThat(first.get("cached").asBoolean()).isFalse();
        summarize(owner, "STORY", story).andExpect(status().isOk()).andExpect(jsonPath("$.data.cached").value(true));
        verify(llm, times(1)).summarize(anyString());

        Map<String, Object> row = jdbc.queryForMap("select family_id, model_name, created_by from ai_summary_cache "
                + "where source_type = 'STORY' and source_id = ? and deleted_at is null", story);
        assertThat(row.get("family_id")).isEqualTo(family);
        assertThat(row.get("model_name")).isEqualTo("model-gia");
        assertThat(row.get("created_by")).isNotNull();

        // Nguồn đổi hoặc bị xóa: cache cũ bị xóa mềm, lần sau gọi lại LLM.
        cache.invalidate("STORY", story);
        assertThat(jdbc.queryForObject("select count(*) from ai_summary_cache where source_id = ? and deleted_at is not null",
                Long.class, story)).isEqualTo(1);
        summarize(owner, "STORY", story).andExpect(status().isOk()).andExpect(jsonPath("$.data.cached").value(false));
        verify(llm, times(2)).summarize(anyString());
    }

    @Test
    void otherFamilyAndUnverifiedUsersCannotSummarize() throws Exception {
        String ownerA = newUser();
        created(send("/api/v1/families", ownerA, Map.of("name", "Dòng họ A")));
        UUID story = created(send("/api/v1/heritage/stories", ownerA, Map.of("title", "Chuyện A", "content", "Nội dung A.")));
        UUID postA = created(send("/api/v1/posts", ownerA, Map.of("content", "Bài của gia đình A", "type", "POST")));

        String ownerB = newUser();
        created(send("/api/v1/families", ownerB, Map.of("name", "Dòng họ B")));
        summarize(ownerB, "STORY", story).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
        summarize(ownerB, "POST", postA).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        String unverified = newUser();
        summarize(unverified, "STORY", story).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_002"));
        summarize(ownerA, "PERSON", story).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALID_001"));
        verify(llm, never()).summarize(anyString());
    }
}
