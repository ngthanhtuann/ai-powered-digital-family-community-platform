-- SCRUM-47: hỗ trợ tìm kiếm hồ sơ theo nghề nghiệp mà không đưa địa chỉ chi tiết vào chỉ mục AI.
ALTER TABLE person ADD COLUMN occupation VARCHAR(120);
