-- [5-04] Announcement và notification thuộc module Community.
-- Không tạo FK xuyên module cho family/branch/user hoặc ref_id (quyết định 9).
CREATE TABLE announcement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    branch_id UUID,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ
);

CREATE INDEX idx_announcement_family_pin ON announcement (family_id, is_pinned DESC, created_at DESC)
    WHERE deleted_at IS NULL AND hidden_at IS NULL;
CREATE INDEX idx_announcement_branch ON announcement (branch_id)
    WHERE deleted_at IS NULL;

CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    ref_type VARCHAR(30),
    ref_id UUID,
    type VARCHAR(50) NOT NULL CHECK (type IN (
        'EVENT_REMINDER_24H', 'EVENT_REMINDER_1H', 'ANNOUNCEMENT',
        'CONTENT_HIDDEN', 'CONTENT_RESTORED',
        'REPORT_HIDDEN', 'REPORT_DISMISSED', 'REPORT_RESTORED'
    )),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_user_read_time ON notification (user_id, is_read, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_notification_family ON notification (family_id)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_notification_user_ref_type ON notification (user_id, ref_id, type)
    WHERE deleted_at IS NULL AND ref_id IS NOT NULL;
