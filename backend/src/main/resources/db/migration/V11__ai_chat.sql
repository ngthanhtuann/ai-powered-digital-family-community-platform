-- Lịch sử hội thoại AI, chỉ module ai truy cập; hết hạn mặc định sau 30 ngày.
CREATE TABLE ai_chat_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ai_chat_session_owner ON ai_chat_session (user_id, family_id, expires_at);

CREATE TABLE ai_chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES ai_chat_session(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    sources JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ai_chat_message_session ON ai_chat_message (session_id, created_at);
CREATE INDEX ix_ai_chat_message_expiry ON ai_chat_message (expires_at);
