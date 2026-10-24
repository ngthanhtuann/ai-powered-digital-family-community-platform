-- SCRUM-41: bảng chỉ mục AI theo docs/03-database/erd.dbml (ai_embedding_chunk) và ai.md mục 4.
-- Không có khóa ngoại sang module khác (architecture.md quyết định 9); extension vector đã bật ở V1.

CREATE TABLE ai_embedding_chunk (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id    UUID         NOT NULL,
    branch_id    UUID,
    source_type  VARCHAR(40)  NOT NULL CHECK (source_type IN ('PERSON', 'POST', 'STORY', 'DOCUMENT', 'EVENT')),
    source_id    UUID         NOT NULL,
    chunk_no     INTEGER      NOT NULL CHECK (chunk_no >= 0),
    content      TEXT         NOT NULL,
    embedding    VECTOR(768)  NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    metadata     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_ai_embedding_chunk_active
    ON ai_embedding_chunk (family_id, source_type, source_id, chunk_no)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_ai_embedding_chunk_source ON ai_embedding_chunk (source_type, source_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_ai_embedding_chunk_family_branch ON ai_embedding_chunk (family_id, branch_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_ai_embedding_chunk_embedding ON ai_embedding_chunk USING hnsw (embedding vector_cosine_ops);
