package com.familyconnect.modules.heritage.service;

import com.familyconnect.modules.auth.api.AccountContentTransfer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** SCRUM-140: chuyển người tạo tài liệu di sản và câu chuyện gia đình sang tài khoản mới. */
@Component
public class HeritageAccountTransfer implements AccountContentTransfer {

    private final JdbcTemplate jdbc;

    public HeritageAccountTransfer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void transferContent(UUID from, UUID to) {
        jdbc.update("UPDATE heritage_document SET created_by = ? WHERE created_by = ?", to, from);
        jdbc.update("UPDATE family_story SET created_by = ? WHERE created_by = ?", to, from);
    }
}
