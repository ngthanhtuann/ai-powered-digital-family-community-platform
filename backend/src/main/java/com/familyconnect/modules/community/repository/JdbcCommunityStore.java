package com.familyconnect.modules.community.repository;

import com.familyconnect.modules.community.dto.CommunityDto.PostType;
import com.familyconnect.modules.community.dto.CommunityDto.ReactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCommunityStore implements CommunityStore {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String POST_COLUMNS =
        "id, family_id, branch_id, content, type, created_by, created_at, updated_at";
    private static final String COMMENT_COLUMNS =
        "id, post_id, content, created_by, created_at, updated_at";
    private static final String REACTION_COLUMNS =
        "id, post_id, type, created_by, created_at, updated_at";

    public JdbcCommunityStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static UUID uuid(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, UUID.class);
    }
    private static OffsetDateTime at(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, OffsetDateTime.class);
    }
    private static final RowMapper<Post> POST_MAPPER = (rs, n) ->
        new Post(uuid(rs, "id"), uuid(rs, "family_id"), uuid(rs, "branch_id"),
                 rs.getString("content"), PostType.valueOf(rs.getString("type")),
                 uuid(rs, "created_by"), at(rs, "created_at"), at(rs, "updated_at"));
    private static final RowMapper<Comment> COMMENT_MAPPER = (rs, n) ->
        new Comment(uuid(rs, "id"), uuid(rs, "post_id"), rs.getString("content"),
                    uuid(rs, "created_by"), at(rs, "created_at"), at(rs, "updated_at"));
    private static final RowMapper<Reaction> REACTION_MAPPER = (rs, n) ->
        new Reaction(uuid(rs, "id"), uuid(rs, "post_id"),
                     ReactionType.valueOf(rs.getString("type")), uuid(rs, "created_by"),
                     at(rs, "created_at"), at(rs, "updated_at"));

    private MapSqlParameterSource p(Object... pairs) {
        var args = new MapSqlParameterSource();
        for (int i = 0; i < pairs.length; i += 2) args.addValue((String) pairs[i], pairs[i + 1]);
        return args;
    }
    private <T> T single(String sql, MapSqlParameterSource args, RowMapper<T> mapper) {
        return jdbc.queryForObject(sql, args, mapper);
    }

    @Override public Post insertPost(UUID family, UUID branch, String content, PostType type, UUID user) {
        return single("INSERT INTO post(family_id,branch_id,content,type,created_by) " +
                      "VALUES (:family,:branch,:content,:type,:user) RETURNING " + POST_COLUMNS,
            p("family",family,"branch",branch,"content",content,"type",type.name(),"user",user), POST_MAPPER);
    }
    @Override public Optional<Post> findPost(UUID id) {
        return jdbc.query("SELECT " + POST_COLUMNS + " FROM post " +
                          "WHERE id=:id AND deleted_at IS NULL AND hidden_at IS NULL",
            p("id",id), POST_MAPPER).stream().findFirst();
    }
    @Override public List<UUID> postBranchIds(UUID family) {
        return jdbc.queryForList("SELECT DISTINCT branch_id FROM post WHERE family_id=:family " +
                                 "AND branch_id IS NOT NULL AND deleted_at IS NULL AND hidden_at IS NULL",
            p("family",family), UUID.class);
    }
    @Override public Page<Post> posts(UUID family, boolean allBranches, List<UUID> visible,
                                       UUID branch, PostType type, int page, int size, String order) {
        var args = p("family",family,"branch",branch,"type",type==null?null:type.name(),
                     "limit",size,"offset",(long)page*size);
        var where = new StringBuilder(" FROM post WHERE family_id=:family AND deleted_at IS NULL " +
                                      "AND hidden_at IS NULL");
        if (!allBranches) {
            if (visible.isEmpty()) where.append(" AND branch_id IS NULL");
            else {
                where.append(" AND (branch_id IS NULL OR branch_id IN (:visible))");
                args.addValue("visible", visible);
            }
        }
        if (branch != null) where.append(" AND branch_id=:branch");
        if (type != null) where.append(" AND type=:type");
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, args, Long.class);
        var items = jdbc.query("SELECT " + POST_COLUMNS + where + " ORDER BY " + order +
                              " LIMIT :limit OFFSET :offset", args, POST_MAPPER);
        return new Page<>(items,total);
    }
    @Override public Post updatePost(UUID id, String content, UUID branch, UUID user) {
        return single("UPDATE post SET content=:content,branch_id=:branch,updated_by=:user," +
                      "updated_at=now() WHERE id=:id AND deleted_at IS NULL RETURNING " + POST_COLUMNS,
            p("id",id,"content",content,"branch",branch,"user",user), POST_MAPPER);
    }
    @Override public void deletePost(UUID id) {
        jdbc.update("UPDATE post SET deleted_at=now(),updated_at=now() WHERE id=:id AND deleted_at IS NULL",
            p("id",id));
    }
    @Override public Map<UUID,Long> commentCounts(List<UUID> postIds) {
        var counts = new HashMap<UUID,Long>();
        if (postIds.isEmpty()) return counts;
        jdbc.query("SELECT post_id,COUNT(*) AS total FROM comment WHERE post_id IN (:posts) " +
                   "AND deleted_at IS NULL AND hidden_at IS NULL GROUP BY post_id", p("posts",postIds),
            rs -> { counts.put(uuid(rs,"post_id"), rs.getLong("total")); });
        return counts;
    }
    @Override public Map<UUID,Map<ReactionType,Long>> reactionCounts(List<UUID> postIds) {
        var result = new HashMap<UUID,Map<ReactionType,Long>>();
        for (var id: postIds) {
            var counts = new EnumMap<ReactionType,Long>(ReactionType.class);
            for (var type: ReactionType.values()) counts.put(type,0L);
            result.put(id,counts);
        }
        if (postIds.isEmpty()) return result;
        jdbc.query("SELECT post_id,type,COUNT(*) AS total FROM reaction WHERE post_id IN (:posts) " +
                   "AND deleted_at IS NULL GROUP BY post_id,type", p("posts",postIds),
            rs -> { result.get(uuid(rs,"post_id")).put(ReactionType.valueOf(rs.getString("type")), rs.getLong("total")); });
        return result;
    }
    @Override public Map<UUID,ReactionType> myReactions(List<UUID> postIds, UUID userId) {
        var mine = new HashMap<UUID,ReactionType>();
        if (postIds.isEmpty()) return mine;
        jdbc.query("SELECT post_id,type FROM reaction WHERE post_id IN (:posts) AND created_by=:user " +
                   "AND deleted_at IS NULL", p("posts",postIds,"user",userId),
            rs -> { mine.put(uuid(rs,"post_id"), ReactionType.valueOf(rs.getString("type"))); });
        return mine;
    }
    @Override public Optional<Reaction> myReaction(UUID postId, UUID userId) {
        return jdbc.query("SELECT " + REACTION_COLUMNS + " FROM reaction WHERE post_id=:post " +
                          "AND created_by=:user AND deleted_at IS NULL", p("post",postId,"user",userId),
            REACTION_MAPPER).stream().findFirst();
    }
    @Override public Comment insertComment(UUID postId, String content, UUID user) {
        return single("INSERT INTO comment(post_id,content,created_by) VALUES (:post,:content,:user) " +
                      "RETURNING " + COMMENT_COLUMNS,
            p("post",postId,"content",content,"user",user), COMMENT_MAPPER);
    }
    @Override public Optional<Comment> findComment(UUID id) {
        return jdbc.query("SELECT " + COMMENT_COLUMNS + " FROM comment " +
                          "WHERE id=:id AND deleted_at IS NULL AND hidden_at IS NULL",
            p("id",id), COMMENT_MAPPER).stream().findFirst();
    }
    @Override public Page<Comment> comments(UUID postId, int page, int size, String order) {
        var args = p("post",postId,"limit",size,"offset",(long)page*size);
        var where = " FROM comment WHERE post_id=:post AND deleted_at IS NULL AND hidden_at IS NULL";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, args, Long.class);
        var items = jdbc.query("SELECT " + COMMENT_COLUMNS + where + " ORDER BY " + order +
                              " LIMIT :limit OFFSET :offset", args, COMMENT_MAPPER);
        return new Page<>(items,total);
    }
    @Override public Comment updateComment(UUID id, String content, UUID user) {
        return single("UPDATE comment SET content=:content,updated_by=:user,updated_at=now() " +
                      "WHERE id=:id AND deleted_at IS NULL RETURNING " + COMMENT_COLUMNS,
            p("id",id,"content",content,"user",user), COMMENT_MAPPER);
    }
    @Override public void deleteComment(UUID id) {
        jdbc.update("UPDATE comment SET deleted_at=now(),updated_at=now() WHERE id=:id AND deleted_at IS NULL",
            p("id",id));
    }
    @Override public void lockPost(UUID id) {
        jdbc.query("SELECT id FROM post WHERE id=:id FOR UPDATE", p("id",id), rs -> {});
    }
    @Override public Reaction insertReaction(UUID postId, UUID user, ReactionType type) {
        return single("INSERT INTO reaction(post_id,created_by,type) VALUES (:post,:user,:type) " +
                      "RETURNING " + REACTION_COLUMNS,
            p("post",postId,"user",user,"type",type.name()), REACTION_MAPPER);
    }
    @Override public Reaction updateReaction(UUID id, UUID user, ReactionType type) {
        return single("UPDATE reaction SET type=:type,updated_by=:user,updated_at=now() " +
                      "WHERE id=:id AND deleted_at IS NULL RETURNING " + REACTION_COLUMNS,
            p("id",id,"user",user,"type",type.name()), REACTION_MAPPER);
    }
    @Override public void deleteReaction(UUID id) {
        jdbc.update("UPDATE reaction SET deleted_at=now(),updated_at=now() " +
                    "WHERE id=:id AND deleted_at IS NULL", p("id",id));
    }
}
