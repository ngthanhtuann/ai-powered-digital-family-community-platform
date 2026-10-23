
CREATE TABLE public.ai_embedding_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    family_id UUID NOT NULL,
    branch_id UUID,

    source_type VARCHAR(20) NOT NULL
        CHECK (source_type IN ('PERSON', 'POST', 'STORY')),

    source_id UUID NOT NULL,
    chunk_no INTEGER NOT NULL DEFAULT 0,

    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding VECTOR(768) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,

    created_by UUID,
    updated_by UUID,

    CONSTRAINT chk_ai_embedding_chunk_no
        CHECK (chunk_no >= 0)
);

CREATE INDEX idx_ai_embedding_chunk_family
    ON public.ai_embedding_chunk (family_id);

CREATE INDEX idx_ai_embedding_chunk_source
    ON public.ai_embedding_chunk
    (family_id, source_type, source_id);

CREATE INDEX idx_ai_embedding_chunk_branch
    ON public.ai_embedding_chunk (branch_id);

CREATE INDEX idx_ai_embedding_chunk_embedding_hnsw
    ON public.ai_embedding_chunk
    USING hnsw (embedding vector_cosine_ops);

CREATE UNIQUE INDEX uq_ai_embedding_chunk_active
    ON public.ai_embedding_chunk
    (family_id, source_type, source_id, chunk_no)
    WHERE deleted_at IS NULL;
