-- SCRUM-37: bảng của module Gia phả theo docs/03-database/erd.dbml và data-dictionary.md.
-- Khóa ngoại chỉ trong module; users, media là tham chiếu logic (UUID có index, kiểm tra ở service).

CREATE TABLE family (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    created_by  UUID         NOT NULL,
    updated_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_family_created_by ON family (created_by);

CREATE TABLE branch (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id        UUID         NOT NULL REFERENCES family (id),
    name             VARCHAR(150) NOT NULL,
    parent_branch_id UUID         REFERENCES branch (id),
    origin_person_id UUID,
    description      TEXT,
    created_by       UUID         NOT NULL,
    updated_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT chk_branch_parent_not_self CHECK (parent_branch_id IS NULL OR parent_branch_id <> id)
);

CREATE INDEX idx_branch_family ON branch (family_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_branch_parent ON branch (parent_branch_id);

CREATE TABLE person (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id        UUID         NOT NULL REFERENCES family (id),
    branch_id        UUID         REFERENCES branch (id),
    full_name        VARCHAR(150) NOT NULL,
    gender           VARCHAR(10)  NOT NULL CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    birth_date       DATE,
    birth_year       SMALLINT,
    birthplace       VARCHAR(255),
    current_province VARCHAR(100),
    current_address  VARCHAR(255),
    is_deceased      BOOLEAN      NOT NULL DEFAULT FALSE,
    death_date       DATE,
    burial_place     VARCHAR(255),
    avatar_media_id  UUID,
    user_id          UUID,
    note             TEXT,
    created_by       UUID         NOT NULL,
    updated_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT chk_person_death_after_birth CHECK (death_date IS NULL OR birth_date IS NULL OR death_date >= birth_date)
);

CREATE UNIQUE INDEX ux_person_user ON person (user_id) WHERE user_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_person_family ON person (family_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_person_branch ON person (branch_id);
CREATE INDEX idx_person_family_province ON person (family_id, current_province) WHERE deleted_at IS NULL;
CREATE INDEX idx_person_avatar ON person (avatar_media_id);

-- branch và person tham chiếu nhau, nên thêm khóa này sau khi có bảng person.
ALTER TABLE branch ADD CONSTRAINT fk_branch_origin_person FOREIGN KEY (origin_person_id) REFERENCES person (id);
CREATE INDEX idx_branch_origin_person ON branch (origin_person_id);

CREATE TABLE parent_child (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID        NOT NULL REFERENCES person (id),
    child_id    UUID        NOT NULL REFERENCES person (id),
    parent_role VARCHAR(10) NOT NULL CHECK (parent_role IN ('FATHER', 'MOTHER')),
    kind        VARCHAR(12) NOT NULL DEFAULT 'BIOLOGICAL' CHECK (kind IN ('BIOLOGICAL', 'ADOPTED', 'STEP')),
    created_by  UUID        NOT NULL,
    updated_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT chk_parent_child_not_self CHECK (parent_id <> child_id)
);

CREATE UNIQUE INDEX ux_parent_child_pair ON parent_child (parent_id, child_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_parent_child_biological_role ON parent_child (child_id, parent_role)
    WHERE kind = 'BIOLOGICAL' AND deleted_at IS NULL;
CREATE INDEX idx_parent_child_child ON parent_child (child_id) WHERE deleted_at IS NULL;

CREATE TABLE marriage (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    person1_id UUID        NOT NULL REFERENCES person (id),
    person2_id UUID        NOT NULL REFERENCES person (id),
    status     VARCHAR(10) NOT NULL CHECK (status IN ('MARRIED', 'DIVORCED', 'WIDOWED')),
    start_date DATE,
    end_date   DATE,
    created_by UUID        NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_marriage_not_self CHECK (person1_id <> person2_id)
);

CREATE INDEX idx_marriage_person1 ON marriage (person1_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_marriage_person2 ON marriage (person2_id) WHERE deleted_at IS NULL;
