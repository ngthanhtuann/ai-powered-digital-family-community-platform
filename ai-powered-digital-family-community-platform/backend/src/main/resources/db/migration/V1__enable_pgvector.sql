-- SCRUM-16: bật phần mở rộng pgvector để dùng cho tìm kiếm ngữ nghĩa (SCRUM-41, SCRUM-49).
-- Các bảng nghiệp vụ được thêm bằng các migration V2, V3, ... ở các task sau.
CREATE EXTENSION IF NOT EXISTS vector;
