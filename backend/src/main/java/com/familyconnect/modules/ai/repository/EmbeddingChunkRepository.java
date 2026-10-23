
package com.familyconnect.modules.ai.repository;

import com.familyconnect.modules.ai.dto.EmbeddingChunkData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.UUID;

@Repository
public class EmbeddingChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(EmbeddingChunkData chunk) {
        if (chunk.familyId() == null) {
            throw new IllegalArgumentException(
                    "familyId must not be null");
        }

        if (chunk.embedding() == null
                || chunk.embedding().length != 768) {
            throw new IllegalArgumentException(
                    "Embedding must have 768 dimensions");
        }

        String vector = Arrays.toString(chunk.embedding())
                .replace("[", "[")
                .replace("]", "]");

        jdbcTemplate.update("""
            INSERT INTO public.ai_embedding_chunk (
                id, family_id, branch_id,
                source_type, source_id, chunk_no,
                content, content_hash, embedding,
                created_at, updated_at
            )
            VALUES (
                gen_random_uuid(), ?, ?,
                ?, ?, ?,
                ?, ?, CAST(? AS vector),
                NOW(), NOW()
            )
            """,
            chunk.familyId(),
            chunk.branchId(),
            chunk.sourceType(),
            chunk.sourceId(),
            chunk.chunkNo(),
            chunk.content(),
            chunk.contentHash(),
            vector
        );
    }
    public void softDeleteActiveBySource(
        java.util.UUID familyId,
        String sourceType,
        java.util.UUID sourceId) {

        jdbcTemplate.update("""
            UPDATE public.ai_embedding_chunk
            SET deleted_at = NOW(),
                updated_at = NOW()
            WHERE family_id = ?
            AND source_type = ?
            AND source_id = ?
            AND deleted_at IS NULL
            """,
            familyId,
            sourceType,
            sourceId
        );
    }
    public long countActiveBySource(
        UUID familyId,
        String sourceType,
        UUID sourceId) {

        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM public.ai_embedding_chunk
            WHERE family_id = ?
            AND source_type = ?
            AND source_id = ?
            AND deleted_at IS NULL
            """,
            Long.class,
            familyId,
            sourceType,
            sourceId
        );

        return count == null ? 0 : count;
    }
}
