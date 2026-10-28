-- SCRUM-48: directory-owned profiles. person_id is a logical reference to genealogy.
CREATE TABLE profession_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    person_id UUID NOT NULL,
    job_title VARCHAR(150) NOT NULL,
    company VARCHAR(200),
    work_location VARCHAR(200),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_profession_profile_person_active
    ON profession_profile (person_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_profession_profile_family ON profession_profile (family_id);
CREATE INDEX idx_profession_profile_created_by ON profession_profile (created_by);
CREATE INDEX idx_profession_profile_updated_by ON profession_profile (updated_by);

CREATE TABLE education_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    person_id UUID NOT NULL,
    school VARCHAR(200) NOT NULL,
    degree VARCHAR(100),
    graduation_year INT CHECK (graduation_year IS NULL OR graduation_year >= 1900),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_education_profile_person_active
    ON education_profile (person_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_education_profile_family ON education_profile (family_id);
CREATE INDEX idx_education_profile_created_by ON education_profile (created_by);
CREATE INDEX idx_education_profile_updated_by ON education_profile (updated_by);
