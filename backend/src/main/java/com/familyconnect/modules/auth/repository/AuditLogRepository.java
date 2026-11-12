package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.dto.AdminDtos.AuditLogData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bảng audit_log (V17): chỉ thêm và đọc, không có câu lệnh sửa hay xóa (UC-AUTH-10, BR-AUTH-16). */
@Repository
public class AuditLogRepository {

    private static final TypeReference<Map<String, Object>> DETAIL = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditLogRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(UUID actorUserId, String actorRole, String action, String targetType, UUID targetId,
                       UUID familyId, String ipAddress, Map<String, Object> detail) {
        jdbc.update("""
                INSERT INTO audit_log (actor_user_id, actor_role, action, target_type, target_id, family_id,
                                       ip_address, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, actorUserId, actorRole, action, targetType, targetId, familyId, ipAddress, toJson(detail));
    }

    /** Lọc theo người, hành động, khoảng thời gian; điều kiện null thì bỏ qua. */
    public List<AuditLogData> search(UUID actorUserId, String action, OffsetDateTime from, OffsetDateTime to,
                                     boolean ascending, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(actorUserId, action, from, to, args);
        String order = ascending ? "ASC" : "DESC";
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT * FROM audit_log" + where + " ORDER BY created_at " + order + ", id " + order
                + " LIMIT ? OFFSET ?", rowMapper(), args.toArray());
    }

    public long count(UUID actorUserId, String action, OffsetDateTime from, OffsetDateTime to) {
        List<Object> args = new ArrayList<>();
        Long total = jdbc.queryForObject("SELECT count(*) FROM audit_log" + where(actorUserId, action, from, to, args),
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static String where(UUID actorUserId, String action, OffsetDateTime from, OffsetDateTime to,
                                List<Object> args) {
        List<String> conditions = new ArrayList<>();
        if (actorUserId != null) {
            conditions.add("actor_user_id = ?");
            args.add(actorUserId);
        }
        if (action != null) {
            conditions.add("action = ?");
            args.add(action);
        }
        if (from != null) {
            conditions.add("created_at >= ?");
            args.add(from);
        }
        if (to != null) {
            conditions.add("created_at <= ?");
            args.add(to);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private RowMapper<AuditLogData> rowMapper() {
        return (rs, i) -> new AuditLogData(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getString("ip_address"),
                fromJson(rs.getString("detail")),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("detail của audit log không đổi được sang JSON", e);
        }
    }

    private Map<String, Object> fromJson(String detail) {
        if (detail == null) {
            return null;
        }
        try {
            return json.readValue(detail, DETAIL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("detail trong audit_log không phải JSON hợp lệ", e);
        }
    }
}
