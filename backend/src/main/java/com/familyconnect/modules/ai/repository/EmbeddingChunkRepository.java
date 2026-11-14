package com.familyconnect.modules.ai.repository;

import com.familyconnect.modules.ai.dto.EmbeddingChunkData;
import com.familyconnect.modules.ai.dto.SemanticSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /** Các chi có nội dung trong chỉ mục của gia đình; service lọc lại theo quyền xem (BR-AI-13). */
    public List<UUID> findBranchIds(UUID familyId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT branch_id FROM ai_embedding_chunk
                WHERE family_id = ? AND branch_id IS NOT NULL AND deleted_at IS NULL
                """, UUID.class, familyId);
    }

    /** Top-k ngữ cảnh cho chat, lọc theo gia đình/chi và hồ sơ trẻ em ngay trong SQL. */
    public List<ChatChunk> searchChat(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                                      float[] embedding, List<UUID> excludedPersonIds, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(Arrays.toString(embedding));
        args.add(familyId);
        String branchFilter = branchFilter(allBranches, visibleBranches, args);
        args.add(jsonIds(excludedPersonIds));
        args.add(Arrays.toString(embedding));
        args.add(limit);
        return jdbcTemplate.query("""
                SELECT c.source_type, c.source_id, c.content, 1 - (c.embedding <=> CAST(? AS vector)) AS score
                FROM ai_embedding_chunk c
                WHERE c.family_id = ? AND c.deleted_at IS NULL
                """ + branchFilter + """
                  AND NOT (c.source_type = 'PERSON' AND EXISTS (
                      SELECT 1 FROM jsonb_array_elements_text(CAST(? AS jsonb)) AS hidden(id)
                      WHERE hidden.id::uuid = c.source_id
                  ))
                ORDER BY c.embedding <=> CAST(? AS vector), c.source_type, c.source_id
                LIMIT ?
                """, (rs, rowNum) -> new ChatChunk(rs.getString("source_type"),
                rs.getObject("source_id", UUID.class), rs.getString("content"), rs.getDouble("score")), args.toArray());
    }

    public record ChatChunk(String sourceType, UUID sourceId, String content, double score) { }

    /**
     * Vector ngữ cảnh = trung bình embedding đã lưu của nguồn, chỉ khi người gọi được xem nguồn đó
     * (cùng gia đình, chi được xem, không phải hồ sơ trẻ em bị ẩn). Null nếu không xem được hoặc chưa có chỉ mục.
     */
    public String findVisibleSourceVector(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                                          List<UUID> excludedPersonIds, String sourceType, UUID sourceId) {
        List<Object> args = new ArrayList<>();
        args.add(familyId);
        String scope = visibleScope(allBranches, visibleBranches, excludedPersonIds, args);
        args.add(sourceType);
        args.add(sourceId);
        return jdbcTemplate.queryForObject("""
                SELECT CAST(AVG(c.embedding) AS text) FROM ai_embedding_chunk c
                WHERE c.family_id = ? AND c.deleted_at IS NULL
                """ + scope + """
                  AND c.source_type = ? AND c.source_id = ?
                """, String.class, args.toArray());
    }

    /** Nguồn gần nhất với vector ngữ cảnh, cùng phạm vi quyền như tìm kiếm (BR-AI-02, BR-AI-13); bỏ chính nguồn đang xem. */
    public List<Recommendation> recommendations(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                                                List<UUID> excludedPersonIds, String contextType, UUID contextId,
                                                String contextVector, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(contextVector);
        args.add(familyId);
        String scope = visibleScope(allBranches, visibleBranches, excludedPersonIds, args);
        args.add(contextType);
        args.add(contextId);
        args.add(limit);
        return jdbcTemplate.query("""
                WITH ranked AS (
                    SELECT c.source_type, c.source_id, c.content,
                           1 - (c.embedding <=> CAST(? AS vector)) AS score
                    FROM ai_embedding_chunk c
                    WHERE c.family_id = ? AND c.deleted_at IS NULL
                """ + scope + """
                      AND NOT (c.source_type = ? AND c.source_id = ?)
                ), best AS (
                    SELECT DISTINCT ON (source_type, source_id) source_type, source_id, content, score
                    FROM ranked ORDER BY source_type, source_id, score DESC
                )
                SELECT source_type, source_id, content, score FROM best
                ORDER BY score DESC, source_type, source_id LIMIT ?
                """, (rs, rowNum) -> {
            String type = rs.getString("source_type");
            String content = rs.getString("content");
            UUID id = rs.getObject("source_id", UUID.class);
            String title = content.lines().findFirst().orElse("Nội dung gia đình");
            if ("PERSON".equals(type) && title.startsWith("Họ tên: ")) title = title.substring(8).replaceFirst("\\.$", "");
            if (title.length() > 90) title = title.substring(0, 87) + "…";
            String snippet = content.length() > 240 ? content.substring(0, 237) + "…" : content;
            return new Recommendation(type, id, title, snippet, rs.getDouble("score"), path(type, id));
        }, args.toArray());
    }

    public record Recommendation(String sourceType, UUID sourceId, String title, String snippet,
                                 double score, String path) { }

    /**
     * Tìm trong family_id và các chi được xem trước khi xếp hạng (BR-AI-02, BR-AI-13).
     * {@code allBranches} = true với SYSTEM_ADMIN và trưởng chi toàn gia đình; còn lại chỉ chunk toàn gia đình
     * (branch_id NULL) hoặc thuộc {@code visibleBranches}.
     */
    public List<SemanticSearchResult> search(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                                              float[] embedding, double minimumScore,
                                              List<UUID> excludedPersonIds, int page, int size) {
        List<Object> args = new ArrayList<>();
        args.add(Arrays.toString(embedding));
        args.add(familyId);
        String branchFilter = branchFilter(allBranches, visibleBranches, args);
        args.add(jsonIds(excludedPersonIds));
        args.add(minimumScore);
        args.add(size);
        args.add(page * size);
        return jdbcTemplate.query("""
                WITH ranked AS (
                    SELECT c.source_type, c.source_id, c.content,
                           1 - (c.embedding <=> CAST(? AS vector)) AS score
                    FROM ai_embedding_chunk c
                    WHERE c.family_id = ? AND c.deleted_at IS NULL
                """ + branchFilter + """
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
        }, args.toArray());
    }

    public long countSearch(UUID familyId, boolean allBranches, List<UUID> visibleBranches,
                            List<UUID> excludedPersonIds, float[] embedding, double minimumScore) {
        List<Object> args = new ArrayList<>();
        args.add(familyId);
        String branchFilter = branchFilter(allBranches, visibleBranches, args);
        args.add(jsonIds(excludedPersonIds));
        args.add(Arrays.toString(embedding));
        args.add(minimumScore);
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT (c.source_type, c.source_id)) FROM ai_embedding_chunk c
                WHERE c.family_id = ? AND c.deleted_at IS NULL
                """ + branchFilter + """
                  AND NOT (c.source_type = 'PERSON' AND EXISTS (
                      SELECT 1 FROM jsonb_array_elements_text(CAST(? AS jsonb)) AS hidden(id)
                      WHERE hidden.id::uuid = c.source_id
                  ))
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                """, Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    /** Điều kiện phạm vi chi; thêm tham số vào {@code args} theo đúng thứ tự dấu ?. */
    private static String branchFilter(boolean allBranches, List<UUID> visibleBranches, List<Object> args) {
        if (allBranches) return "";
        if (visibleBranches.isEmpty()) return "  AND c.branch_id IS NULL\n";
        args.addAll(visibleBranches);
        return "  AND (c.branch_id IS NULL OR c.branch_id IN ("
                + String.join(",", Collections.nCopies(visibleBranches.size(), "?")) + "))\n";
    }

    /** Phạm vi chi + ẩn hồ sơ trẻ em; thêm tham số vào {@code args} theo đúng thứ tự dấu ?. */
    private static String visibleScope(boolean allBranches, List<UUID> visibleBranches,
                                       List<UUID> excludedPersonIds, List<Object> args) {
        String branch = branchFilter(allBranches, visibleBranches, args);
        args.add(jsonIds(excludedPersonIds));
        return branch + """
                  AND NOT (c.source_type = 'PERSON' AND EXISTS (
                      SELECT 1 FROM jsonb_array_elements_text(CAST(? AS jsonb)) AS hidden(id)
                      WHERE hidden.id::uuid = c.source_id
                  ))
                """;
    }

    private static String jsonIds(List<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static String path(String type, UUID id) {
        return switch (type) {
            case "PERSON" -> "/persons/" + id;
            case "POST", "EVENT" -> "/community?item=" + id;
            case "STORY" -> "/heritage/stories/" + id;
            case "DOCUMENT" -> "/heritage/documents/" + id;
            case "PHOTO" -> "/heritage/photos?mediaId=" + id;
            default -> "/";
        };
    }
}
