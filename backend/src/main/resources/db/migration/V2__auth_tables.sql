-- SCRUM-35: bảng module auth theo docs/01-srs/use-cases/auth.md mục 5.2 và erd.dbml.
-- Khóa ngoại chỉ giữa các bảng trong module này (architecture.md quyết định 9).
-- Cột người thực hiện (created_by, updated_by, reviewed_by) chỉ lưu UUID, không FK (quyết định 4).

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(20),
    date_of_birth DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED')),
    lock_reason   VARCHAR(255),
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    created_by    UUID,
    updated_by    UUID
);
CREATE UNIQUE INDEX ux_users_email ON users (email) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_status ON users (status);

CREATE TABLE role (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE user_role (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    role_id    UUID        NOT NULL REFERENCES role (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_user_role_user_role ON user_role (user_id, role_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_user_role_role ON user_role (role_id);

CREATE TABLE refresh_token (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_expires ON refresh_token (expires_at);

CREATE TABLE member_verification (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users (id),
    family_id     UUID         NOT NULL,
    branch_id     UUID,
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'BRANCH_ADMIN')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REMOVED')),
    message       VARCHAR(255),
    reject_reason VARCHAR(255),
    reviewed_by   UUID,
    reviewed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    created_by    UUID,
    updated_by    UUID
);
-- Mỗi người tối đa một bản ghi đang hiệu lực (BR-AUTH-08).
CREATE UNIQUE INDEX ux_member_verification_active_user ON member_verification (user_id)
    WHERE status IN ('PENDING', 'APPROVED') AND deleted_at IS NULL;
CREATE INDEX ix_member_verification_family_status ON member_verification (family_id, status);
CREATE INDEX ix_member_verification_branch ON member_verification (branch_id);

INSERT INTO role (code, name, description) VALUES
    ('GUEST',        'Khách',              'Người chưa đăng nhập; không gán vào user_role'),
    ('MEMBER',       'Thành viên',         'Vai trò mặc định của mọi tài khoản'),
    ('BRANCH_ADMIN', 'Trưởng chi',         'Gắn với gia đình và chi, lưu ở member_verification.role'),
    ('SYSTEM_ADMIN', 'Quản trị hệ thống',  'Vai trò toàn hệ thống');
