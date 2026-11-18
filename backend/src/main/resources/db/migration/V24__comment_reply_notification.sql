-- SCRUM-123, BR-COM-14: trả lời bình luận (1 cấp) và thông báo khi có bình luận, trả lời.
ALTER TABLE comment ADD COLUMN parent_id UUID REFERENCES comment (id);
CREATE INDEX idx_comment_parent ON comment (parent_id) WHERE parent_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
    'EVENT_REMINDER_24H', 'EVENT_REMINDER_1H', 'ANNOUNCEMENT',
    'CONTENT_HIDDEN', 'CONTENT_RESTORED',
    'REPORT_HIDDEN', 'REPORT_DISMISSED', 'REPORT_RESTORED',
    'POST_COMMENTED', 'COMMENT_REPLIED'));
