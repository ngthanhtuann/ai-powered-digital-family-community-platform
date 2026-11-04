package com.familyconnect.modules.ai;

import com.familyconnect.modules.ai.service.ChatLanguageModel;
import com.familyconnect.modules.ai.service.ChatService;
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /ai/chat (UC-AI-02) với PostgreSQL thật; embedding và LLM giả lập nên không gọi Gemini, mỗi test tự rollback.
 * Bao phủ: lưu lịch sử đúng cột ai.md mục 5.3, phiên của người khác (PERM_001), không đưa trẻ em hoặc gia đình khác vào
 * ngữ cảnh, xóa mềm khi hết thời hạn lưu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiChatApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";
    private static final float[] VECTOR = new float[768];

    static {
        Arrays.fill(VECTOR, 0.1f);
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChatService chatService;
    @MockBean private EmbeddingPipelineService embedding;
    @MockBean private ChatLanguageModel model;

    @BeforeEach
    void fakeAi() {
        when(embedding.isConfigured()).thenReturn(true);
        when(embedding.createEmbedding(anyString())).thenReturn(VECTOR);
        when(model.isConfigured()).thenReturn(true);
        when(model.modelName()).thenReturn("model-gia");
        when(model.answer(anyString())).thenReturn("Câu trả lời giả từ ngữ cảnh.");
    }

    private String newUser() throws Exception {
        String email = "chat." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    private ResultActions send(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private UUID family(String owner) throws Exception {
        return UUID.fromString(data(send("/api/v1/families", owner, Map.of("name", "Dòng họ thử"))
                .andExpect(status().isCreated())).get("id").asText());
    }

    private UUID chunk(UUID familyId, String type, UUID sourceId) {
        UUID id = sourceId == null ? UUID.randomUUID() : sourceId;
        jdbc.update("""
                insert into ai_embedding_chunk(family_id, source_type, source_id, chunk_no, content, embedding, content_hash)
                values (?, ?, ?, 0, ?, CAST(? AS vector), 'hash')
                """, familyId, type, id, "Hồ sơ giả " + type, Arrays.toString(VECTOR));
        return id;
    }

    private Map<String, Object> ask(UUID sessionId) {
        Map<String, Object> body = new HashMap<>(Map.of("question", "Ông tổ là ai?"));
        if (sessionId != null) body.put("sessionId", sessionId.toString());
        return body;
    }

    @Test
    void answerIsSavedToHistoryAndSessionBelongsToOwnerOnly() throws Exception {
        String owner = newUser();
        UUID familyId = family(owner);
        UUID source = chunk(familyId, "STORY", null);
        chunk(UUID.randomUUID(), "STORY", null);

        JsonNode first = data(send("/api/v1/ai/chat", owner, ask(null)).andExpect(status().isOk()));
        assertThat(first.get("answer").asText()).isEqualTo("Câu trả lời giả từ ngữ cảnh.");
        assertThat(first.get("sources")).hasSize(1);
        assertThat(first.get("sources").get(0).get("sourceId").asText()).isEqualTo(source.toString());
        UUID session = UUID.fromString(first.get("sessionId").asText());

        send("/api/v1/ai/chat", owner, ask(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(session.toString()));
        assertThat(jdbc.queryForObject("select count(*) from ai_chat_message where session_id = ?", Long.class, session))
                .isEqualTo(4);
        Map<String, Object> reply = jdbc.queryForMap("select model_name, latency_ms from ai_chat_message "
                + "where session_id = ? and role = 'ASSISTANT' order by created_at limit 1", session);
        assertThat(reply.get("model_name")).isEqualTo("model-gia");
        assertThat(reply.get("latency_ms")).isNotNull();
        assertThat(jdbc.queryForObject("select last_message_at is not null from ai_chat_session where id = ?",
                Boolean.class, session)).isTrue();

        String stranger = newUser();
        family(stranger);
        send("/api/v1/ai/chat", stranger, ask(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }

    @Test
    void minorProfileIsNeverSentToModel() throws Exception {
        String owner = newUser();
        UUID familyId = family(owner);
        Map<String, Object> kid = new HashMap<>(Map.of("fullName", "Bé Na", "gender", "FEMALE", "birthDate", "2018-05-01"));
        UUID minor = UUID.fromString(data(send("/api/v1/families/" + familyId + "/persons", owner, kid)
                .andExpect(status().isCreated())).get("id").asText());
        chunk(familyId, "PERSON", minor);

        send("/api/v1/ai/chat", owner, ask(null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("Không tìm thấy thông tin."))
                .andExpect(jsonPath("$.data.sources.length()").value(0));
        verify(model, never()).answer(anyString());
    }

    @Test
    void expiredHistoryIsSoftDeletedAndSessionCannotBeReused() throws Exception {
        String owner = newUser();
        UUID familyId = family(owner);
        chunk(familyId, "STORY", null);
        UUID session = UUID.fromString(data(send("/api/v1/ai/chat", owner, ask(null)).andExpect(status().isOk()))
                .get("sessionId").asText());
        jdbc.update("update ai_chat_message set created_at = now() - interval '40 days' where session_id = ?", session);
        jdbc.update("update ai_chat_session set created_at = now() - interval '40 days', "
                + "last_message_at = now() - interval '40 days' where id = ?", session);

        chatService.purgeExpiredHistory();

        assertThat(jdbc.queryForObject("select count(*) from ai_chat_message where session_id = ? and deleted_at is null",
                Long.class, session)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_chat_message where session_id = ?", Long.class, session))
                .isEqualTo(2);
        send("/api/v1/ai/chat", owner, ask(session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }
}
