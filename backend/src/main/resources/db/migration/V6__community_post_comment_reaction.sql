-- SCRUM-38 [3-04]: bài đăng, bình luận, reaction theo erd.dbml (post, comment, reaction).
-- Đánh số sau V5 (genealogy, SCRUM-37); khóa ngoại chỉ trong module community.
CREATE TABLE post (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    branch_id UUID,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('POST', 'NEWS')),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ
);

CREATE TABLE comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES post(id),
    content TEXT NOT NULL,
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    hidden_at TIMESTAMPTZ
);

CREATE TABLE reaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES post(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('LIKE', 'LOVE', 'HAHA', 'SAD')),
    created_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_post_family_created ON post(family_id, created_at DESC);
CREATE INDEX idx_post_branch ON post(branch_id);
CREATE INDEX idx_post_created_by ON post(created_by);
CREATE INDEX idx_post_updated_by ON post(updated_by);
CREATE INDEX idx_comment_post_created ON comment(post_id, created_at);
CREATE INDEX idx_comment_created_by ON comment(created_by);
CREATE INDEX idx_comment_updated_by ON comment(updated_by);
CREATE INDEX idx_reaction_created_by ON reaction(created_by);
CREATE INDEX idx_reaction_updated_by ON reaction(updated_by);
CREATE UNIQUE INDEX uq_reaction_active_post_user
    ON reaction(post_id, created_by) WHERE deleted_at IS NULL;
