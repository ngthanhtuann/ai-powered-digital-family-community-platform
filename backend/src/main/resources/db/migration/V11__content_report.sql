-- SCRUM-50: báo cáo nội dung vi phạm theo erd.dbml và data-dictionary.md (content_report, BR-COM-12).
-- family_id, target_id, reporter_id, created_by, updated_by là tham chiếu logic, không FK chéo module.
CREATE TABLE content_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('POST', 'COMMENT', 'EVENT', 'ANNOUNCEMENT')),
    target_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reason VARCHAR(255) NOT NULL,
    content_snippet VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'HIDDEN', 'DISMISSED')),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_content_report_target_reporter
    ON content_report(target_type, target_id, reporter_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_content_report_status_created ON content_report(status, created_at);
