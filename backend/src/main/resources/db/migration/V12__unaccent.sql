-- SCRUM-50 (UC-AUTH-08, auth.yaml tham số q): tìm người dùng không phân biệt dấu tiếng Việt ("nguyen" khớp "Nguyễn").
-- unaccent là extension có sẵn của PostgreSQL (contrib), không tạo bảng.
CREATE EXTENSION IF NOT EXISTS unaccent;
