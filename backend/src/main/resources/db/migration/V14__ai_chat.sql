-- SCRUM-55: lịch sử hội thoại AI theo ai.md mục 5.2, 5.3 và erd.dbml. Chỉ module ai đọc, ghi.
-- Không có khóa ngoại sang module khác; session_id là FK nội bộ cùng module, xóa mềm nên dùng RESTRICT.

CREATE TABLE ai_chat_session (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    title           VARCHAR(200),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    created_by      UUID,
    updated_by      UUID
);
CREATE INDEX ix_ai_chat_session_family_user ON ai_chat_session (family_id, user_id, updated_at DESC);
CREATE INDEX ix_ai_chat_session_user_deleted ON ai_chat_session (user_id, deleted_at);

CREATE TABLE ai_chat_message (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID          NOT NULL REFERENCES ai_chat_session (id) ON DELETE RESTRICT,
    role          VARCHAR(20)   NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content       TEXT          NOT NULL,
    sources       JSONB         NOT NULL DEFAULT '[]'::jsonb,
    model_name    VARCHAR(100),
    input_tokens  INTEGER       CHECK (input_tokens >= 0),
    output_tokens INTEGER       CHECK (output_tokens >= 0),
    latency_ms    INTEGER       CHECK (latency_ms >= 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    created_by    UUID,
    updated_by    UUID
);
CREATE INDEX ix_ai_chat_message_session ON ai_chat_message (session_id, created_at);
