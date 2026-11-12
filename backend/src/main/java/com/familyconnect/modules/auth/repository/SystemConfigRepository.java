package com.familyconnect.modules.auth.repository;

import com.familyconnect.modules.auth.dto.SystemConfigDtos.ConfigValueType;
import com.familyconnect.modules.auth.dto.SystemConfigDtos.SystemConfigData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng system_config (V20): chỉ đọc và đổi giá trị khóa có sẵn, không thêm hay xóa khóa (BR-AUTH-18). */
@Repository
public class SystemConfigRepository {

    private static final String COLUMNS = """
            SELECT config_key, config_value, value_type, config_group, default_value, description, updated_by, updated_at
            FROM system_config WHERE deleted_at IS NULL
            """;
    private static final RowMapper<SystemConfigData> ROW = (rs, row) -> new SystemConfigData(
            rs.getString("config_key"), rs.getString("config_value"),
            ConfigValueType.valueOf(rs.getString("value_type")), rs.getString("config_group"),
            rs.getString("default_value"), rs.getString("description"),
            rs.getObject("updated_by", UUID.class), rs.getObject("updated_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public SystemConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SystemConfigData> findAll() {
        return jdbc.query(COLUMNS + " ORDER BY config_group, config_key", ROW);
    }

    /** Khóa row để hai quản trị đổi cùng lúc không ghi nhật ký sai giá trị cũ. */
    public Optional<SystemConfigData> findForUpdate(String key) {
        return jdbc.query(COLUMNS + " AND config_key = ? FOR UPDATE", ROW, key).stream().findFirst();
    }

    public UUID updateValue(String key, String value, UUID actorId) {
        return jdbc.queryForObject("""
                UPDATE system_config SET config_value = ?, updated_by = ?, updated_at = now()
                WHERE config_key = ? AND deleted_at IS NULL RETURNING id
                """, UUID.class, value, actorId, key);
    }
}
