package com.familyconnect.modules.ai.repository;

import com.familyconnect.modules.ai.dto.ChatResponse.SourceReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bảng ai_chat_session, ai_chat_message (ai.md mục 5.2, 5.3). Xóa luôn là xóa mềm. */
@Repository
public class ChatRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ChatRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public UUID createSession(UUID userId, UUID familyId) {
        return jdbc.queryForObject("INSERT INTO ai_chat_session(user_id,family_id,created_by,updated_by) VALUES (?,?,?,?) RETURNING id",
                UUID.class, userId, familyId, userId, userId);
    }
    public boolean ownsActiveSession(UUID sessionId, UUID userId, UUID familyId) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_chat_session WHERE id=? AND user_id=? AND family_id=? "
                        + "AND status='ACTIVE' AND deleted_at IS NULL)",
                Boolean.class, sessionId, userId, familyId);
        return Boolean.TRUE.equals(found);
    }
    /** Hết thời hạn lưu: xóa mềm tin nhắn cũ và phiên không còn hoạt động từ trước {@code cutoff}. */
    @Transactional
    public void softDeleteOlderThan(Instant cutoff) {
        Timestamp at = Timestamp.from(cutoff);
        jdbc.update("UPDATE ai_chat_message SET deleted_at=now(), updated_at=now() WHERE deleted_at IS NULL AND created_at < ?", at);
        jdbc.update("UPDATE ai_chat_session SET deleted_at=now(), updated_at=now() WHERE deleted_at IS NULL "
                + "AND COALESCE(last_message_at, created_at) < ?", at);
    }
    public List<HistoryMessage> recentMessages(UUID sessionId, int limit) {
        return jdbc.query("SELECT role,content FROM (SELECT role,content,created_at FROM ai_chat_message WHERE session_id=? AND deleted_at IS NULL "
                        + "ORDER BY created_at DESC LIMIT ?) recent ORDER BY created_at",
                (rs, n) -> new HistoryMessage(rs.getString(1), rs.getString(2)), sessionId, limit);
    }
    @Transactional
    public UUID saveExchange(UUID sessionId, UUID userId, String question, String answer, List<SourceReference> sources,
                             String modelName, long latencyMs) {
        String json;
        try { json = mapper.writeValueAsString(sources); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        jdbc.update("INSERT INTO ai_chat_message(session_id,role,content,created_by,updated_by) VALUES (?,'USER',?,?,?)",
                sessionId, question, userId, userId);
        UUID id = jdbc.queryForObject("INSERT INTO ai_chat_message(session_id,role,content,sources,model_name,latency_ms,created_by,updated_by) "
                        + "VALUES (?,'ASSISTANT',?,CAST(? AS jsonb),?,?,?,?) RETURNING id",
                UUID.class, sessionId, answer, json, modelName, (int) Math.min(latencyMs, Integer.MAX_VALUE), userId, userId);
        jdbc.update("UPDATE ai_chat_session SET last_message_at=now(), updated_at=now(), updated_by=? WHERE id=?", userId, sessionId);
        return id;
    }
    public record HistoryMessage(String role, String content) { }
}
