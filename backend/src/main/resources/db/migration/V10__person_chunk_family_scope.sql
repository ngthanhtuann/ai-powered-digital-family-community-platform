-- SCRUM-49: hồ sơ thành viên cả gia đình được xem (UC-GEN-03), nên chunk PERSON là phạm vi toàn gia đình (BR-AI-13).
-- Chunk tạo trước SCRUM-49 còn mang branch_id của người đó; đặt lại NULL thay vì lập chỉ mục lại (không gọi Gemini).
UPDATE ai_embedding_chunk
SET branch_id = NULL, updated_at = now()
WHERE source_type = 'PERSON' AND branch_id IS NOT NULL;
