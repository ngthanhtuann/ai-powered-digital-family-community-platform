-- SCRUM-61 (UC-DIR-01): chỉ mục trigram cho lọc danh bạ theo tỉnh/thành, nghề (LIKE '%...%' trên lower()).
-- Họ tên tìm không dấu bằng unaccent (V12) trong phạm vi một gia đình nên không có chỉ mục riêng, như tìm users.
-- V17 dành cho audit_log (SCRUM-57). pg_trgm là extension có sẵn của PostgreSQL (contrib), không tạo bảng.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_person_active_province_trgm
    ON person USING GIN (lower(current_province) gin_trgm_ops)
    WHERE deleted_at IS NULL AND current_province IS NOT NULL;

CREATE INDEX idx_profession_profile_active_job_title_trgm
    ON profession_profile USING GIN (lower(job_title) gin_trgm_ops)
    WHERE deleted_at IS NULL;
