package com.familyconnect.modules.community.media;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMediaRepository implements MediaRepository {
    private static final String COLUMNS = "id, owner_type, owner_id, family_id, file_size, " +
        "file_name, storage_key, caption, file_type, created_by, created_at, updated_at";
    private static final RowMapper<Media> MAPPER = (rs, row) -> new Media(
        uuid(rs, "id"), rs.getString("owner_type"), uuid(rs, "owner_id"),
        uuid(rs, "family_id"), rs.getLong("file_size"), rs.getString("file_name"),
        rs.getString("storage_key"), rs.getString("caption"), rs.getString("file_type"),
        uuid(rs, "created_by"), rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMediaRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static MapSqlParameterSource p(Object... fields) {
        var params = new MapSqlParameterSource();
        for (int i = 0; i < fields.length; i += 2) params.addValue((String) fields[i], fields[i + 1]);
        return params;
    }

    @Override
    public Media insert(UUID id, String ownerType, UUID ownerId, UUID familyId, long size,
                        String fileName, String storageKey, String caption, String mimeType,
                        UUID createdBy) {
        return jdbc.queryForObject("INSERT INTO media (id, owner_type, owner_id, family_id, " +
                "file_size, file_name, storage_key, caption, file_type, created_by) " +
                "VALUES (:id, :type, :owner, :family, :size, :name, :key, :caption, :mime, :user) " +
                "RETURNING " + COLUMNS,
            p("id", id, "type", ownerType, "owner", ownerId, "family", familyId,
              "size", size, "name", fileName, "key", storageKey, "caption", caption,
              "mime", mimeType, "user", createdBy), MAPPER);
    }

    @Override
    public Optional<Media> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM media WHERE id=:id AND deleted_at IS NULL",
            p("id", id), MAPPER).stream().findFirst();
    }

    @Override
    public Page list(String ownerType, UUID ownerId, int page, int size, String order) {
        var args = p("type", ownerType, "owner", ownerId, "limit", size, "offset", (long) page * size);
        String where = " FROM media WHERE owner_type=:type AND owner_id=:owner AND deleted_at IS NULL";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, args, Long.class);
        var items = jdbc.query("SELECT " + COLUMNS + where + " ORDER BY " + order +
            " LIMIT :limit OFFSET :offset", args, MAPPER);
        return new Page(items, total == null ? 0 : total);
    }

    @Override
    public boolean softDelete(UUID id, UUID actorId) {
        return jdbc.update("UPDATE media SET deleted_at=now(), updated_at=now(), updated_by=:user " +
            "WHERE id=:id AND deleted_at IS NULL", p("id", id, "user", actorId)) == 1;
    }
}
