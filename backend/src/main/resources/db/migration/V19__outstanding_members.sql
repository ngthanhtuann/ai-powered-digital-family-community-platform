-- SCRUM-62 (UC-HER-04): bảng người tiêu biểu theo data-dictionary.md mục outstanding_member.
-- family_id, person_id, created_by là UUID có chỉ mục, không khóa ngoại sang module Gia phả hoặc Auth (quyết định 9).
CREATE TABLE outstanding_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    person_id UUID NOT NULL,
    achievement TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0 CHECK (display_order >= 0),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_outstanding_person
    ON outstanding_member (family_id, person_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_outstanding_display
    ON outstanding_member (family_id, display_order)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_outstanding_person ON outstanding_member (person_id);
CREATE INDEX idx_outstanding_creator ON outstanding_member (created_by);
