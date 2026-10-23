CREATE TABLE heritage_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    media_id UUID NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_heritage_doc_media ON heritage_document (media_id);
CREATE INDEX idx_heritage_doc_family ON heritage_document (family_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_heritage_doc_creator ON heritage_document (created_by);

CREATE TABLE family_story (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    related_person_id UUID,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_family_story_family ON family_story (family_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_family_story_related ON family_story (related_person_id)
    WHERE deleted_at IS NULL AND related_person_id IS NOT NULL;
CREATE INDEX idx_family_story_creator ON family_story (created_by);
