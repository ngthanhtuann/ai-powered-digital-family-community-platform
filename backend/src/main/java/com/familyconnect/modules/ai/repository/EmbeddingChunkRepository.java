package com.familyconnect.modules.ai.repository;

import com.familyconnect.modules.ai.dto.EmbeddingChunkData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Đọc ghi bảng ai_embedding_chunk; chỉ module ai dùng (architecture.md quyết định 1). */
@Repository
public class EmbeddingChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(EmbeddingChunkData chunk, String metadataJson) {
        if (chunk.familyId() == null) {
            throw new IllegalArgumentException("familyId must not be null");
        }
        if (chunk.embedding() == null || chunk.embedding().length != 768) {
            throw new IllegalArgumentException("Embedding must have 768 dimensions");
        }
        jdbcTemplate.update("""
                INSERT INTO ai_embedding_chunk (
                    family_id, branch_id, source_type, source_id, chunk_no,
                    content, content_hash, embedding, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), CAST(? AS jsonb))
                """,
                chunk.familyId(), chunk.branchId(), chunk.sourceType(), chunk.sourceId(), chunk.chunkNo(),
                chunk.content(), chunk.contentHash(), Arrays.toString(chunk.embedding()), metadataJson);
    }

    /** Hash các chunk đang hiệu lực theo thứ tự chunk_no, để bỏ qua nguồn không đổi. */
    public List<String> findActiveHashes(String sourceType, UUID sourceId) {
        return jdbcTemplate.queryForList("""
                SELECT content_hash FROM ai_embedding_chunk
                WHERE source_type = ? AND source_id = ? AND deleted_at IS NULL
                ORDER BY chunk_no
                """, String.class, sourceType, sourceId);
    }

    public int softDeleteActiveBySource(String sourceType, UUID sourceId) {
        return jdbcTemplate.update("""
                UPDATE ai_embedding_chunk SET deleted_at = now(), updated_at = now()
                WHERE source_type = ? AND source_id = ? AND deleted_at IS NULL
                """, sourceType, sourceId);
    }

    public long countActiveBySource(String sourceType, UUID sourceId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_embedding_chunk
                WHERE source_type = ? AND source_id = ? AND deleted_at IS NULL
                """, Long.class, sourceType, sourceId);
        return count == null ? 0 : count;
    }
}
