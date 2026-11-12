-- [6-02] SCRUM-57: nhật ký kiểm toán (data-dictionary.md mục audit_log, UC-AUTH-10).
-- Bản ghi chỉ thêm nên không có updated_at, deleted_at. actor_user_id, target_id, family_id không có FK.
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    actor_role    VARCHAR(20),
    action        VARCHAR(60) NOT NULL,
    target_type   VARCHAR(40),
    target_id     UUID,
    family_id     UUID,
    ip_address    VARCHAR(45),
    detail        JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action ON audit_log (action, created_at DESC);
