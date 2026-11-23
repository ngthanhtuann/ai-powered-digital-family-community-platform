-- SCRUM-139 (UC-AUTH-15, BR-AUTH-24): mã đặt lại mật khẩu dùng một lần do trưởng họ hoặc quản trị cấp.
CREATE TABLE password_reset_code (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id),
    code_hash       VARCHAR(100) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    used_at         TIMESTAMPTZ,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_password_reset_code_user ON password_reset_code (user_id) WHERE used_at IS NULL AND deleted_at IS NULL;
