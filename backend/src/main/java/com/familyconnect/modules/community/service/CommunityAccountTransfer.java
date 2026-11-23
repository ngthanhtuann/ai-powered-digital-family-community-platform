package com.familyconnect.modules.community.service;

import com.familyconnect.modules.auth.api.AccountContentTransfer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** BR-COM-16: chuyển người tạo bài đăng, bình luận, cảm xúc, ảnh, thông báo gia đình; cảm xúc trùng bài giữ bản của tài khoản mới. */
@Component
public class CommunityAccountTransfer implements AccountContentTransfer {

    private final JdbcTemplate jdbc;

    public CommunityAccountTransfer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void transferContent(UUID from, UUID to) {
        jdbc.update("UPDATE reaction r SET deleted_at = now(), updated_at = now() WHERE r.created_by = ? AND r.deleted_at IS NULL AND EXISTS (SELECT 1 FROM reaction x WHERE x.post_id = r.post_id AND x.created_by = ? AND x.deleted_at IS NULL)", from, to);
        jdbc.update("UPDATE reaction SET created_by = ? WHERE created_by = ? AND deleted_at IS NULL", to, from);
        jdbc.update("UPDATE post SET created_by = ? WHERE created_by = ?", to, from);
        jdbc.update("UPDATE comment SET created_by = ? WHERE created_by = ?", to, from);
        jdbc.update("UPDATE media SET created_by = ? WHERE created_by = ?", to, from);
        jdbc.update("UPDATE announcement SET created_by = ? WHERE created_by = ?", to, from);
    }
}
