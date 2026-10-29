-- [4-04] Media thuộc module Community, theo docs/03-database/erd.dbml.
-- owner_id và family_id là tham chiếu logic; không khai báo FK xuyên module.
CREATE TABLE media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type VARCHAR(20) NOT NULL CHECK (owner_type IN ('POST', 'EVENT', 'HERITAGE', 'PERSON')),
    owner_id UUID NOT NULL,
    family_id UUID NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    caption VARCHAR(255),
    file_type VARCHAR(100) NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_media_owner ON media (owner_type, owner_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_family ON media (family_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_creator ON media (created_by);
