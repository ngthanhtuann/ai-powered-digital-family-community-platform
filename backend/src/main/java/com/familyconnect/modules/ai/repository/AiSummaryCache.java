package com.familyconnect.modules.ai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

/** Bảng ai_summary_cache (ai.md mục 5.4): khóa cache là gia đình, nguồn và hash nội dung; xóa luôn là xóa mềm. */
@Repository
public class AiSummaryCache {
    private final JdbcTemplate jdbc;
    public AiSummaryCache(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<String> find(UUID familyId, String type, UUID sourceId, String hash) {
        return jdbc.query("select summary from ai_summary_cache where family_id = ? and source_type = ? and source_id = ? "
                        + "and source_version_hash = ? and deleted_at is null and expires_at > now()",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), familyId, type, sourceId, hash);
    }

    public void save(UUID familyId, String type, UUID sourceId, String hash, String summary, String model, UUID actor) {
        jdbc.update("insert into ai_summary_cache(family_id,source_type,source_id,source_version_hash,summary,model_name,"
                        + "expires_at,created_by,updated_by) values (?,?,?,?,?,?,now()+interval '30 days',?,?) "
                        + "on conflict (family_id, source_type, source_id, source_version_hash) where deleted_at is null "
                        + "do update set summary=excluded.summary, model_name=excluded.model_name, "
                        + "expires_at=excluded.expires_at, updated_at=now(), updated_by=excluded.updated_by",
                familyId, type, sourceId, hash, summary, model, actor, actor);
    }

    /** Nguồn đổi nội dung, bị xóa hoặc bị ẩn: vô hiệu hóa mọi bản tóm tắt cũ của nguồn (BR-AI-09). */
    public void invalidate(String type, UUID sourceId) {
        jdbc.update("update ai_summary_cache set deleted_at = now(), updated_at = now() "
                + "where source_type = ? and source_id = ? and deleted_at is null", type, sourceId);
    }
}
