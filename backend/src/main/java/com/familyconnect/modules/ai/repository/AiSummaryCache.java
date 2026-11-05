package com.familyconnect.modules.ai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiSummaryCache {
    private final JdbcTemplate jdbc;
    public AiSummaryCache(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<String> find(String hash) {
        return jdbc.query("select summary from ai_summary_cache where source_version_hash = ? and deleted_at is null and expires_at > now()", rs ->
                rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), hash);
    }
    public void save(String hash, UUID familyId, String type, UUID sourceId, String summary) {
        jdbc.update("insert into ai_summary_cache(id,family_id,source_type,source_id,source_version_hash,summary,model_name,expires_at) " +
                "values (gen_random_uuid(),?,?,?,?,?,'gemini',now()+interval '30 days') " +
                "on conflict (source_version_hash) where deleted_at is null do update set " +
                "family_id=excluded.family_id, source_type=excluded.source_type, source_id=excluded.source_id, " +
                "summary=excluded.summary, updated_at=now(), expires_at=excluded.expires_at",
                familyId, type, sourceId, hash, summary);
    }
    public void invalidate(String type, UUID sourceId) {
        jdbc.update("delete from ai_summary_cache where source_type=? and source_id=?", type, sourceId);
    }
}
