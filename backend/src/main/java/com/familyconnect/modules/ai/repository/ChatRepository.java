package com.familyconnect.modules.ai.repository;

import com.familyconnect.modules.ai.dto.ChatResponse.SourceReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ChatRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ChatRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public UUID createSession(UUID userId, UUID familyId, Instant expiresAt) {
        return jdbc.queryForObject("INSERT INTO ai_chat_session(user_id,family_id,expires_at) VALUES (?,?,?) RETURNING id",
                UUID.class, userId, familyId, expiresAt);
    }
    public boolean ownsActiveSession(UUID sessionId, UUID userId, UUID familyId, Instant now) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_chat_session WHERE id=? AND user_id=? AND family_id=? AND expires_at>?)",
                Boolean.class, sessionId, userId, familyId, now);
        return Boolean.TRUE.equals(found);
    }
    @Scheduled(fixedDelay = 3600000)
    public void purgeExpiredSessions() {
        jdbc.update("DELETE FROM ai_chat_session WHERE expires_at <= now()");
    }
    public List<HistoryMessage> recentMessages(UUID sessionId, int limit) {
        return jdbc.query("SELECT role,content FROM (SELECT role,content,created_at FROM ai_chat_message WHERE session_id=? AND expires_at>now() ORDER BY created_at DESC LIMIT ?) recent ORDER BY created_at",
                (rs, n) -> new HistoryMessage(rs.getString(1), rs.getString(2)), sessionId, limit);
    }
    @Transactional
    public UUID saveExchange(UUID sessionId, String question, String answer, List<SourceReference> sources, Instant expiresAt) {
        String json;
        try { json = mapper.writeValueAsString(sources); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        jdbc.update("INSERT INTO ai_chat_message(session_id,role,content,expires_at) VALUES (?,'USER',?,?)", sessionId, question, expiresAt);
        UUID id = jdbc.queryForObject("INSERT INTO ai_chat_message(session_id,role,content,sources,expires_at) VALUES (?,'ASSISTANT',?,CAST(? AS jsonb),?) RETURNING id",
                UUID.class, sessionId, answer, json, expiresAt);
        jdbc.update("UPDATE ai_chat_session SET updated_at=now() WHERE id=?", sessionId);
        return id;
    }
    public record HistoryMessage(String role, String content) { }
}
