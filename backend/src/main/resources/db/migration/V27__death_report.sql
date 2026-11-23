-- SCRUM-135 (BR-GEN-24, BR-COM-17): con ruột báo mất, chờ xác nhận, hết hạn 7 ngày, gợi ý kế nhiệm.
CREATE TABLE death_report (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id           UUID NOT NULL REFERENCES family (id),
    person_id           UUID NOT NULL REFERENCES person (id),
    death_date          DATE NOT NULL,
    death_time          TIME,
    burial_place        VARCHAR(255),
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED')),
    expires_at          TIMESTAMPTZ NOT NULL,
    confirmed_by        UUID,
    confirmed_at        TIMESTAMPTZ,
    successor_person_id UUID REFERENCES person (id),
    created_by          UUID NOT NULL,
    updated_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_death_report_pending ON death_report (person_id) WHERE status = 'PENDING' AND deleted_at IS NULL;
CREATE INDEX idx_death_report_family ON death_report (family_id) WHERE deleted_at IS NULL;

ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'EVENT_REMINDER_24H', 'EVENT_REMINDER_1H', 'ANNOUNCEMENT',
    'CONTENT_HIDDEN', 'CONTENT_RESTORED',
    'REPORT_HIDDEN', 'REPORT_DISMISSED', 'REPORT_RESTORED',
    'POST_COMMENTED', 'COMMENT_REPLIED', 'DEATH_NOTICE', 'DEATH_REPORT'));
