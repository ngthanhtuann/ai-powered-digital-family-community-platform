package com.familyconnect.modules.ai.repository;

import com.familyconnect.modules.ai.dto.EmbeddingChunkData;
import com.familyconnect.modules.ai.dto.SemanticSearchResult;
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

    /** Search only within the authorized family and branch subtree before ranking candidates. */
    public List<SemanticSearchResult> search(UUID familyId, UUID branchId,
                                              float[] embedding, double minimumScore,
                                              List<UUID> excludedPersonIds, int page, int size) {
        String vector = Arrays.toString(embedding);
        String excludedIds = excludedPersonIds.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_branches(id) AS (
                    SELECT id FROM branch WHERE id = ? AND family_id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT child.id FROM branch child JOIN visible_branches parent ON child.parent_branch_id = parent.id
                    WHERE child.family_id = ? AND child.deleted_at IS NULL
                ), ranked AS (
                    SELECT c.source_type, c.source_id, c.content,
                           1 - (c.embedding <=> CAST(? AS vector)) AS score
                    FROM ai_embedding_chunk c
                    WHERE c.family_id = ? AND c.deleted_at IS NULL
                      AND (c.branch_id IS NULL OR CAST(? AS uuid) IS NULL OR c.branch_id IN (SELECT id FROM visible_branches))
                      AND NOT (c.source_type = 'PERSON' AND EXISTS (
                          SELECT 1 FROM jsonb_array_elements_text(CAST(? AS jsonb)) AS hidden(id)
                          WHERE hidden.id::uuid = c.source_id
                      ))
                ), best_per_source AS (
                    SELECT DISTINCT ON (source_type, source_id) source_type, source_id, content, score
                    FROM ranked ORDER BY source_type, source_id, score DESC
                )
                SELECT source_type, source_id, content, score FROM best_per_source
                WHERE score >= ?
                ORDER BY score DESC, source_type, source_id
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> {
            String content = rs.getString("content");
            String title = content.lines().findFirst().orElse("Kết quả gia đình");
            if ("PERSON".equals(rs.getString("source_type")) && title.startsWith("Họ tên: ")) {
                title = title.substring("Họ tên: ".length()).replaceFirst("\\.$", "");
            }
            if (title.length() > 90) title = title.substring(0, 87) + "…";
            String snippet = content.length() > 240 ? content.substring(0, 237) + "…" : content;
            String type = rs.getString("source_type");
            UUID id = rs.getObject("source_id", UUID.class);
            return new SemanticSearchResult(type, id, title, snippet, rs.getDouble("score"), path(type, id));
        }, branchId, familyId, familyId, vector, familyId, branchId, excludedIds,
                minimumScore, size, page * size);
    }

    public long countSearch(UUID familyId, UUID branchId, List<UUID> excludedPersonIds, float[] embedding, double minimumScore) {
        String vector = Arrays.toString(embedding);
        String excludedIds = excludedPersonIds.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        Long count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE visible_branches(id) AS (
                    SELECT id FROM branch WHERE id = ? AND family_id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT child.id FROM branch child JOIN visible_branches parent ON child.parent_branch_id = parent.id
                    WHERE child.family_id = ? AND child.deleted_at IS NULL
                )
                SELECT COUNT(DISTINCT (c.source_type, c.source_id)) FROM ai_embedding_chunk c
                WHERE c.family_id = ? AND c.deleted_at IS NULL
                  AND (c.branch_id IS NULL OR CAST(? AS uuid) IS NULL OR c.branch_id IN (SELECT id FROM visible_branches))
                  AND NOT (c.source_type = 'PERSON' AND EXISTS (
                      SELECT 1 FROM jsonb_array_elements_text(CAST(? AS jsonb)) AS hidden(id)
                      WHERE hidden.id::uuid = c.source_id
                  ))
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                """, Long.class, branchId, familyId, familyId, familyId, branchId, excludedIds,
                vector, minimumScore);
        return count == null ? 0 : count;
    }

    private static String path(String type, UUID id) {
        return switch (type) {
            case "PERSON" -> "/persons/" + id;
            case "POST", "EVENT" -> "/community?item=" + id;
            case "STORY" -> "/heritage/stories/" + id;
            case "DOCUMENT" -> "/heritage/documents/" + id;
            default -> "/";
        };
    }
}
