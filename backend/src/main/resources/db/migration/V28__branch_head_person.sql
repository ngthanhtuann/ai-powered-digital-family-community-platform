-- SCRUM-146 (BR-GEN-29): trưởng chi chọn tay, kể cả người chưa có tài khoản; NULL thì theo dòng (BR-GEN-25).
ALTER TABLE branch ADD COLUMN head_person_id UUID REFERENCES person (id);
CREATE INDEX idx_branch_head_person ON branch (head_person_id) WHERE head_person_id IS NOT NULL AND deleted_at IS NULL;
