-- SCRUM-94, UC-AUTH-12: tham số cấu hình hệ thống (auth.md mục 5 bảng system_config, mục 5.3 dữ liệu khởi tạo).
-- Chỉ đổi giá trị của khóa đã khởi tạo ở đây; API không tạo hoặc xóa khóa (BR-AUTH-18).
-- Khóa bí mật (JWT secret, khóa LLM, mật khẩu DB) chỉ nằm ở biến môi trường, không đưa vào bảng này.
-- created_by, updated_by chỉ lưu UUID, không FK (architecture.md quyết định 4).
CREATE TABLE system_config (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key    VARCHAR(100) NOT NULL,
    config_value  VARCHAR(500) NOT NULL,
    value_type    VARCHAR(20)  NOT NULL CHECK (value_type IN ('INTEGER', 'BOOLEAN', 'STRING')),
    config_group  VARCHAR(50)  NOT NULL,
    default_value VARCHAR(500) NOT NULL,
    description   VARCHAR(255) NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_system_config_key ON system_config (config_key) WHERE deleted_at IS NULL;

INSERT INTO system_config (config_key, config_value, value_type, config_group, default_value, description) VALUES
    ('app.name', 'FamilyConnect', 'STRING', 'Hệ thống', 'FamilyConnect',
     'Tên hiển thị của hệ thống hoặc gia đình (1 đến 100 ký tự).'),
    ('ai.search.enabled', 'true', 'BOOLEAN', 'AI', 'true', 'Bật hoặc tắt tìm kiếm ngữ nghĩa.'),
    ('ai.chat.enabled', 'true', 'BOOLEAN', 'AI', 'true', 'Bật hoặc tắt trợ lý AI (chatbot).'),
    ('ai.summary.enabled', 'true', 'BOOLEAN', 'AI', 'true', 'Bật hoặc tắt tóm tắt nội dung bằng AI.'),
    ('ai.chat.limit_per_hour', '20', 'INTEGER', 'AI', '20',
     'Số câu hỏi trợ lý AI tối đa mỗi người mỗi giờ (1 đến 200).'),
    ('upload.max_image_mb', '5', 'INTEGER', 'Tệp tải lên', '5', 'Dung lượng tối đa mỗi ảnh, tính bằng MB (1 đến 5).'),
    ('upload.allowed_image_types', 'jpg,png,webp', 'STRING', 'Tệp tải lên', 'jpg,png,webp',
     'Định dạng ảnh cho phép, là tập con của jpg,png,webp.'),
    ('membership.approval_mode', 'MANUAL', 'STRING', 'Thành viên', 'MANUAL',
     'Chế độ duyệt thành viên mới: MANUAL (trưởng họ, trưởng chi duyệt) hoặc AUTO (duyệt ngay).'),
    ('auth.access_token_minutes', '15', 'INTEGER', 'Bảo mật', '15', 'Thời hạn access token, tính bằng phút (5 đến 60).'),
    ('auth.refresh_token_days', '7', 'INTEGER', 'Bảo mật', '7', 'Thời hạn refresh token, tính bằng ngày (1 đến 30).');
