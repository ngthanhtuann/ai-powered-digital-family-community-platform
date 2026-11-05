-- Cache tóm tắt theo hash văn bản. family_id là thuộc tính có index, không FK liên module.
CREATE TABLE ai_summary_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    source_type VARCHAR(40) NOT NULL CHECK (source_type IN ('POST', 'STORY', 'DOCUMENT')),
    source_id UUID NOT NULL,
    source_version_hash VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '30 days'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_ai_summary_cache_hash ON ai_summary_cache(source_version_hash) WHERE deleted_at IS NULL;
CREATE INDEX ix_ai_summary_cache_family ON ai_summary_cache(family_id);
CREATE INDEX ix_ai_summary_cache_source ON ai_summary_cache(source_type, source_id) WHERE deleted_at IS NULL;
