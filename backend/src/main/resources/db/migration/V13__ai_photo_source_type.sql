-- SCRUM-87: cho phép lưu chỉ mục ảnh di sản theo chú thích ảnh.
ALTER TABLE ai_embedding_chunk
    DROP CONSTRAINT ai_embedding_chunk_source_type_check;

ALTER TABLE ai_embedding_chunk
    ADD CONSTRAINT ai_embedding_chunk_source_type_check
        CHECK (source_type IN ('PERSON', 'POST', 'STORY', 'DOCUMENT', 'EVENT', 'PHOTO'));
