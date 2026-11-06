-- SCRUM-88: cache tóm tắt AI theo ai.md mục 5.4 và erd.dbml. Chỉ module ai đọc, ghi.
-- family_id, source_id chỉ là UUID có index, không FK liên module.
CREATE TABLE ai_summary_cache (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id           UUID          NOT NULL,
    source_type         VARCHAR(40)   NOT NULL,
    source_id           UUID          NOT NULL,
    source_version_hash VARCHAR(64)   NOT NULL,
    summary             TEXT          NOT NULL,
    model_name          VARCHAR(100)  NOT NULL,
    expires_at          TIMESTAMPTZ   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    created_by          UUID,
    updated_by          UUID
);
CREATE UNIQUE INDEX ux_ai_summary_cache_key
    ON ai_summary_cache (family_id, source_type, source_id, source_version_hash)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_ai_summary_cache_expires ON ai_summary_cache (expires_at);
CREATE INDEX ix_ai_summary_cache_source ON ai_summary_cache (source_type, source_id) WHERE deleted_at IS NULL;
