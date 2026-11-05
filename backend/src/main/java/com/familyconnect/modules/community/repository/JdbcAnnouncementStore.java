package com.familyconnect.modules.community.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAnnouncementStore {
    public record Announcement(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               UUID familyId, UUID branchId, String title, String content,
                               boolean isPinned, UUID createdBy) {}
    public record Page(List<Announcement> items, long total) {}

    private static final String COLUMNS = "id, created_at, updated_at, family_id, branch_id, title, " +
        "content, is_pinned, created_by";
    private static final RowMapper<Announcement> MAPPER = (ResultSet r, int n) -> new Announcement(
        r.getObject("id",UUID.class),r.getObject("created_at",OffsetDateTime.class),
        r.getObject("updated_at",OffsetDateTime.class),r.getObject("family_id",UUID.class),
        r.getObject("branch_id",UUID.class),r.getString("title"),r.getString("content"),
        r.getBoolean("is_pinned"),r.getObject("created_by",UUID.class));

    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAnnouncementStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static MapSqlParameterSource p(Object... fields) {
        var result = new MapSqlParameterSource();
        for (int i=0; i<fields.length; i+=2) result.addValue((String)fields[i],fields[i+1]);
        return result;
    }

    public Announcement create(UUID family, UUID branch, String title, String content, boolean pinned, UUID actor) {
        return jdbc.queryForObject("INSERT INTO announcement(family_id,branch_id,title,content,is_pinned,created_by) " +
            "VALUES (:family,:branch,:title,:content,:pin,:actor) RETURNING " + COLUMNS,
            p("family",family,"branch",branch,"title",title,"content",content,"pin",pinned,"actor",actor),MAPPER);
    }

    public Optional<Announcement> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM announcement WHERE id=:id AND deleted_at IS NULL " +
            "AND hidden_at IS NULL",p("id",id),MAPPER).stream().findFirst();
    }

    public List<UUID> branchIds(UUID family) {
        return jdbc.queryForList("SELECT DISTINCT branch_id FROM announcement WHERE family_id=:family " +
            "AND branch_id IS NOT NULL AND deleted_at IS NULL AND hidden_at IS NULL",
            p("family",family),UUID.class);
    }

    public Page list(UUID family, boolean all, List<UUID> readable, UUID branch, int page, int size, String sort) {
        var args = p("family",family,"branch",branch,"limit",size,"offset",(long)page*size);
        StringBuilder where = new StringBuilder(" FROM announcement WHERE family_id=:family " +
            "AND deleted_at IS NULL AND hidden_at IS NULL");
        if (!all) {
            if (readable.isEmpty()) where.append(" AND branch_id IS NULL");
            else { where.append(" AND (branch_id IS NULL OR branch_id IN (:visible))");
                   args.addValue("visible",readable); }
        }
        if (branch != null) where.append(" AND branch_id=:branch");
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where,args,Long.class);
        return new Page(jdbc.query("SELECT " + COLUMNS + where + " ORDER BY is_pinned DESC, " + sort +
            " LIMIT :limit OFFSET :offset",args,MAPPER),total == null ? 0 : total);
    }

    public Announcement update(UUID id, UUID branch, String title, String content, boolean pinned, UUID actor) {
        return jdbc.queryForObject("UPDATE announcement SET branch_id=:branch,title=:title,content=:content," +
            "is_pinned=:pin,updated_by=:actor,updated_at=now() WHERE id=:id AND deleted_at IS NULL " +
            "AND hidden_at IS NULL RETURNING " + COLUMNS,
            p("id",id,"branch",branch,"title",title,"content",content,"pin",pinned,"actor",actor),MAPPER);
    }

    public void delete(UUID id, UUID actor) {
        jdbc.update("UPDATE announcement SET deleted_at=now(),updated_by=:actor,updated_at=now() " +
            "WHERE id=:id AND deleted_at IS NULL",p("id",id,"actor",actor));
    }
}
