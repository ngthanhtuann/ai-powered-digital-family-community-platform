-- [4-05] Events theo docs/03-database/erd.dbml và BR-EVT-01..03.
-- V7 là Directory (SCRUM-48), V8 là media (SCRUM-46).
CREATE TABLE event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    branch_id UUID,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ,
    location VARCHAR(255),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ,
    CONSTRAINT chk_event_time CHECK (end_at IS NULL OR end_at > start_at)
);

CREATE INDEX idx_event_family_start ON event(family_id, start_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_event_branch ON event(branch_id);
CREATE INDEX idx_event_creator ON event(created_by);

CREATE TABLE event_participant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES event(id),
    user_id UUID NOT NULL,
    rsvp_status VARCHAR(20) NOT NULL CHECK (rsvp_status IN ('GOING', 'NOT_GOING', 'MAYBE')),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_event_participant_active ON event_participant(event_id, user_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_event_participant_user ON event_participant(user_id);
CREATE INDEX idx_event_participant_creator ON event_participant(created_by);
