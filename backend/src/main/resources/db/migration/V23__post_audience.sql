-- SCRUM-121, BR-COM-13: người xem bài đăng. FAMILY = cả dòng họ, BRANCH = chi và chi con, DESCENDANTS = con cháu người đăng.
ALTER TABLE post ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'FAMILY';
-- Bài cũ giữ nguyên người xem: có branch_id là phạm vi chi.
UPDATE post SET audience = 'BRANCH' WHERE branch_id IS NOT NULL;
ALTER TABLE post ADD CONSTRAINT chk_post_audience CHECK (
    (audience = 'BRANCH' AND branch_id IS NOT NULL)
    OR (audience IN ('FAMILY', 'DESCENDANTS') AND branch_id IS NULL));
