package com.familyconnect.modules.community.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcNotificationStore {
    public record Notification(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               String type, String title, String refType, UUID refId,
                               String content, boolean isRead) {}
    public record Page(List<Notification> items, long total) {}

    private static final String COLUMNS = "id, created_at, updated_at, type, title, ref_type, ref_id, content, is_read";
    private static final RowMapper<Notification> MAPPER = (ResultSet r, int n) -> new Notification(
        r.getObject("id", UUID.class), r.getObject("created_at", OffsetDateTime.class),
        r.getObject("updated_at", OffsetDateTime.class), r.getString("type"), r.getString("title"),
        r.getString("ref_type"), r.getObject("ref_id", UUID.class), r.getString("content"), r.getBoolean("is_read"));

    private final NamedParameterJdbcTemplate jdbc;
    public JdbcNotificationStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static MapSqlParameterSource p(Object... fields) {
        var values = new MapSqlParameterSource();
        for (int i = 0; i < fields.length; i += 2) values.addValue((String) fields[i], fields[i + 1]);
        return values;
    }

    /** UNIQUE (user_id, ref_id, type) đảm bảo retry/job chạy lại không tạo thông báo trùng. */
    public void send(UUID userId, UUID familyId, String refType, UUID refId, String type,
                     String title, String content, UUID creator) {
        jdbc.update("INSERT INTO notification (user_id, family_id, ref_type, ref_id, type, title, content, created_by) " +
            "VALUES (:user,:family,:refType,:ref,:type,:title,:content,:creator) " +
            "ON CONFLICT (user_id, ref_id, type) WHERE deleted_at IS NULL AND ref_id IS NOT NULL DO NOTHING",
            p("user",userId,"family",familyId,"refType",refType,"ref",refId,"type",type,
              "title",title,"content",content,"creator",creator));
    }

    public Page list(UUID userId, Boolean isRead, int page, int size, String order) {
        var args = p("user",userId,"read",isRead,"limit",size,"offset",(long)page*size);
        String where = " FROM notification WHERE user_id=:user AND deleted_at IS NULL" +
            (isRead == null ? "" : " AND is_read=:read");
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where,args,Long.class);
        return new Page(jdbc.query("SELECT " + COLUMNS + where + " ORDER BY " + order +
            " LIMIT :limit OFFSET :offset",args,MAPPER),total == null ? 0 : total);
    }

    public Notification markRead(UUID id, UUID userId) {
        return jdbc.query("UPDATE notification SET is_read=TRUE, updated_at=now(), updated_by=:user " +
            "WHERE id=:id AND user_id=:user AND deleted_at IS NULL RETURNING " + COLUMNS,
            p("id",id,"user",userId),MAPPER).stream().findFirst().orElse(null);
    }
}
