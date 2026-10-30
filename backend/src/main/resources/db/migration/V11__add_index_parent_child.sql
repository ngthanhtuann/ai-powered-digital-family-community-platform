-- Thêm index tối ưu hóa cho cấu trúc đệ quy (Recursive CTE)
CREATE INDEX IF NOT EXISTS idx_parent_child_parent_id ON parent_child(parent_id);
CREATE INDEX IF NOT EXISTS idx_parent_child_child_id ON parent_child(child_id);
CREATE INDEX IF NOT EXISTS idx_marriage_person1_id ON marriage(person1_id);
CREATE INDEX IF NOT EXISTS idx_marriage_person2_id ON marriage(person2_id);

