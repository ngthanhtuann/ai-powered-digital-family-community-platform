package com.familyconnect.modules.events.service;

import com.familyconnect.modules.auth.api.AccountContentTransfer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** BR-COM-16: chuyển người tạo sự kiện và RSVP; RSVP trùng sự kiện giữ bản của tài khoản mới. */
@Component
public class EventAccountTransfer implements AccountContentTransfer {

    private final JdbcTemplate jdbc;

    public EventAccountTransfer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void transferContent(UUID from, UUID to) {
        jdbc.update("UPDATE event_participant p SET deleted_at = now(), updated_at = now() WHERE p.user_id = ? AND p.deleted_at IS NULL AND EXISTS (SELECT 1 FROM event_participant x WHERE x.event_id = p.event_id AND x.user_id = ? AND x.deleted_at IS NULL)", from, to);
        jdbc.update("UPDATE event_participant SET user_id = ? WHERE user_id = ? AND deleted_at IS NULL", to, from);
        jdbc.update("UPDATE event_participant SET created_by = ? WHERE created_by = ?", to, from);
        jdbc.update("UPDATE event SET created_by = ? WHERE created_by = ?", to, from);
    }
}
