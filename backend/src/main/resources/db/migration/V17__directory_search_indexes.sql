CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_person_active_full_name_trgm
    ON person USING GIN (lower(full_name) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_person_active_province_trgm
    ON person USING GIN (lower(current_province) gin_trgm_ops)
    WHERE deleted_at IS NULL AND current_province IS NOT NULL;

CREATE INDEX idx_profession_profile_active_job_title_trgm
    ON profession_profile USING GIN (lower(job_title) gin_trgm_ops)
    WHERE deleted_at IS NULL;
